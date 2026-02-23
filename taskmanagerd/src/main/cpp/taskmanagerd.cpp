#include <arpa/inet.h>
#include <cerrno>
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <fcntl.h>
#include <limits.h>
#include <pwd.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>
#include <unistd.h>

#include <algorithm>
#include <chrono>
#include <cctype>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <memory>
#include <regex>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#include <dirent.h>

#include "json.hpp"

namespace fs = std::filesystem;

static std::string toLower(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(),
                   [](unsigned char c){ return std::tolower(c); });
    return s;
}

static bool isCpuThermalType(const std::string& type) {
    std::string t = toLower(type);

    // Match common CPU-related thermal types
    return (t.find("cpu") != std::string::npos) ||
           (t.find("soc") != std::string::npos) ||
           (t.find("ap")  != std::string::npos) ||
           (t.find("cluster") != std::string::npos);
}

int getCpuTemperatureCelsius() {
    const std::string basePath = "/sys/class/thermal/";
    DIR* dir = opendir(basePath.c_str());
    if (!dir)
        return -1;

    struct dirent* entry;
    int maxTemp = -1;

    while ((entry = readdir(dir)) != nullptr) {
        std::string name = entry->d_name;

        if (name.find("thermal_zone") == std::string::npos)
            continue;

        std::string zonePath = basePath + name;

        // Read thermal type
        std::ifstream typeFile(zonePath + "/type");
        if (!typeFile.is_open())
            continue;

        std::string type;
        std::getline(typeFile, type);
        typeFile.close();

        if (!isCpuThermalType(type))
            continue;

        // Read temperature
        std::ifstream tempFile(zonePath + "/temp");
        if (!tempFile.is_open())
            continue;

        long raw = 0;
        tempFile >> raw;
        tempFile.close();

        if (raw <= 0)
            continue;

        int tempC;

        // Handle millidegree format (most Android devices)
        if (raw > 1000)
            tempC = static_cast<int>(raw / 1000);
        else
            tempC = static_cast<int>(raw);

        // Filter realistic range
        if (tempC >= 5 && tempC <= 100) {
            maxTemp = std::max(maxTemp, tempC);
        }
    }

    closedir(dir);
    return maxTemp; // -1 if not found
}

static std::regex pid_regex("\\d+");

std::vector<int> listPids() {
    std::vector<int> pids;
    pids.reserve(256);

    for (const auto &entry : fs::directory_iterator("/proc")) {
        // Fix to move the try catch block inside the loop
        try {
            if (entry.is_directory()) {
                std::string name = entry.path().filename();
                if (std::regex_match(name, pid_regex)) {
                    pids.push_back(std::stoi(name));
                }
            }
        } catch (...) {

        }
    }
    return pids;
}

static volatile sig_atomic_t keep_running = 1;

void handle_sigint(int) {
    keep_running = 0;
}

std::string now_str() {
    time_t t = time(nullptr);
    struct tm tm{};
    localtime_r(&t, &tm);
    char buf[64];
    strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", &tm);
    return {buf};
}

void log_line(const std::string &line) {
    std::cout << "[" << now_str() << "] " << line << std::endl;
}

bool send_msg(int sock, const std::string &msg) {
    std::string data = msg + "\n";
    size_t total = 0;
    while (total < data.size()) {
        ssize_t sent = send(sock, data.data() + total, data.size() - total, MSG_NOSIGNAL);
        if (sent <= 0) return false;
        total += sent;
    }
    return true;
}

struct CpuStat {
    long user;
    long nice;
    long system;
    long idle;
    long iowait;
    long irq;
    long softirq;
    long steal;

    long total() const {
        return user + nice + system + idle + iowait + irq + softirq + steal;
    }

    long active() const {
        return total() - idle;
    }
};

CpuStat readCpuStat() {
    std::ifstream file("/proc/stat");
    if (!file.is_open()) {
        return CpuStat{0,0,0,0,0,0,0,0};
    }

    std::string line;
    std::getline(file, line);

    if (line.rfind("cpu ", 0) == 0) {
        std::istringstream iss(line);
        std::string cpuLabel;
        long values[8] = {0};
        iss >> cpuLabel;
        for (int i = 0; i < 8; ++i) {
            if (!(iss >> values[i])) break;
        }
        return CpuStat{values[0], values[1], values[2], values[3],
                       values[4], values[5], values[6], values[7]};
    }

    return CpuStat{0,0,0,0,0,0,0,0};
}

int calculateCpuUsage() {
    CpuStat prev = readCpuStat();
    std::this_thread::sleep_for(std::chrono::milliseconds(100));
    CpuStat curr = readCpuStat();

    uint64_t totalDiff = curr.total() - prev.total();
    uint64_t activeDiff = curr.active() - prev.active();

    if (totalDiff == 0) return 0;

    double usage = (double)activeDiff / (double)totalDiff * 100.0;
    if (usage < 0) usage = 0;
    if (usage > 100) usage = 100;
    return (int)usage;
}

int readInt(const char* path) {
    std::ifstream file(path);
    int value = -1;
    if (file.is_open()) {
        file >> value;
        file.close();
    }
    return value;
}

int calculateGpuUsage() {
    // Try Adreno
    int usage = readInt("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage");
    if (usage > 0)
        return usage;

    // Try Mali
    usage = readInt("/sys/class/misc/mali0/device/utilization");
    if (usage > 0)
        return usage;

    return -1;
}

bool killProcess(int pid) {
    if (kill(pid, SIGKILL) == 0) {
        return true;
    }
    std::cerr << "Failed to kill process " << pid
              << ": " << strerror(errno) << std::endl;
    return false;
}


bool killProcessGroup(pid_t pgid, int signal = SIGKILL) {
    return kill(-pgid, signal) == 0;
}

struct Proc {
    int pid;
    std::string name;
    int nice;
    int uid;
    float cpuUsage;
    int parentPid;
    bool isForeground;
    long memoryUsageKb;
    std::string cmdLine;
    std::string state;
    int threads;
    long startTime;
    float elapsedTime;
    long residentSetSizeKb;
    long virtualMemoryKb;
    std::string cgroup;
    std::string executablePath;
};

long getSystemUptime() {
    std::ifstream uptime("/proc/uptime");
    double uptimeSeconds = 0.0;
    if (uptime.is_open()) {
        uptime >> uptimeSeconds;
    }
    return static_cast<long>(uptimeSeconds * sysconf(_SC_CLK_TCK));
}

float calculateProcessCpuUsage(int pid) {
    std::string statPath = "/proc/" + std::to_string(pid) + "/stat";
    std::ifstream statFile(statPath);

    if (!statFile.is_open()) return 0.0f;

    std::string line;
    std::getline(statFile, line);

    // Parse the stat file
    size_t lastParen = line.rfind(')');
    if (lastParen == std::string::npos) return 0.0f;

    std::istringstream iss(line.substr(lastParen + 2));
    std::string state;
    long utime = 0, stime = 0, starttime = 0;

    // Skip to fields 14 (utime) and 15 (stime)
    for (int i = 0; i < 11; ++i) {
        std::string dummy;
        iss >> dummy;
    }

    iss >> utime >> stime;

    for (int i = 0; i < 6; ++i) {
        std::string dummy;
        iss >> dummy;
    }
    iss >> starttime;

    long totalTime = utime + stime;
    long uptime = getSystemUptime();
    long elapsedTime = uptime - starttime;

    if (elapsedTime > 0) {
        return (100.0f * totalTime) / elapsedTime;
    }
    return 0.0f;
}

bool isForegroundProcess(int pid) {
    std::string oomPath = "/proc/" + std::to_string(pid) + "/oom_score_adj";
    std::ifstream oomFile(oomPath);

    if (!oomFile.is_open()) return false;

    int oomScore = 0;
    oomFile >> oomScore;

    // Android: foreground apps typically have oom_score_adj of 0 or positive
    // Background apps have higher positive values
    return oomScore <= 100;
}

std::string getCgroup(int pid) {
    std::string cgroupPath = "/proc/" + std::to_string(pid) + "/cgroup";
    std::ifstream cgroupFile(cgroupPath);

    if (!cgroupFile.is_open()) return "";

    std::string line;
    if (std::getline(cgroupFile, line)) {
        size_t colonPos = line.find_last_of(':');
        if (colonPos != std::string::npos) {
            return line.substr(colonPos + 1);
        }
    }
    return line;
}

std::string getExecutablePath(int pid) {
    std::string exePath = "/proc/" + std::to_string(pid) + "/exe";
    char path[PATH_MAX];

    ssize_t len = readlink(exePath.c_str(), path, sizeof(path) - 1);
    if (len != -1) {
        path[len] = '\0';
        return std::string(path);
    }
    return "";
}

Proc readProc(int pid) {
    Proc p{};
    p.pid = pid;

    std::string procPath = "/proc/" + std::to_string(pid);

    std::ifstream commFile(procPath + "/comm");
    if (commFile.is_open()) {
        std::getline(commFile, p.name);
    }

    std::ifstream cmdFile(procPath + "/cmdline", std::ios::binary);
    if (cmdFile.is_open()) {
        std::getline(cmdFile, p.cmdLine, '\0');
    }

    std::ifstream statFile(procPath + "/stat");
    if (statFile.is_open()) {
        std::string line;
        std::getline(statFile, line);

        size_t lastParen = line.rfind(')');
        if (lastParen != std::string::npos) {
            std::istringstream iss(line.substr(lastParen + 2));
            std::string dummy;

            // Skip state (field 3)
            iss >> dummy;
            // Skip ppid (field 4) - we get it from status
            iss >> dummy;
            // Skip pgrp (field 5)
            iss >> dummy;
            // Skip session (field 6)
            iss >> dummy;
            // Skip tty_nr (field 7)
            iss >> dummy;
            // Skip tpgid (field 8)
            iss >> dummy;
            // Skip flags (field 9)
            iss >> dummy;
            // Skip minflt through cstime (fields 10-17)
            for (int i = 0; i < 8; ++i) iss >> dummy;

            // Field 18: priority (not used)
            iss >> dummy;
            // Field 19: nice
            iss >> p.nice;

            // Skip to field 22: starttime
            iss >> dummy >> dummy;
            iss >> p.startTime;
        }
    }

    long uptime = getSystemUptime();
    p.elapsedTime = static_cast<float>(uptime - p.startTime) / sysconf(_SC_CLK_TCK);

    std::ifstream statusFile(procPath + "/status");
    std::string line;
    int fieldsFound = 0;
    const int totalFields = 6;

    while (fieldsFound < totalFields && std::getline(statusFile, line)) {
        if (line.compare(0, 4, "Uid:") == 0) {
            p.uid = std::stoi(line.substr(5));
            fieldsFound++;
        } else if (line.compare(0, 5, "PPid:") == 0) {
            p.parentPid = std::stoi(line.substr(6));
            fieldsFound++;
        } else if (line.compare(0, 6, "VmRSS:") == 0) {
            p.residentSetSizeKb = std::stol(line.substr(7));
            p.memoryUsageKb = p.residentSetSizeKb; // Same as RSS
            fieldsFound++;
        } else if (line.compare(0, 7, "VmSize:") == 0) {
            p.virtualMemoryKb = std::stol(line.substr(8));
            fieldsFound++;
        } else if (line.compare(0, 8, "Threads:") == 0) {
            p.threads = std::stoi(line.substr(9));
            fieldsFound++;
        } else if (line.compare(0, 6, "State:") == 0) {
            p.state = line.substr(7);
            fieldsFound++;
        }
    }

    p.cpuUsage = calculateProcessCpuUsage(pid);

    p.isForeground = isForegroundProcess(pid);

    p.cgroup = getCgroup(pid);

    p.executablePath = getExecutablePath(pid);

    return p;
}

nlohmann::json procToJson(const Proc &p) {
    nlohmann::json j;
    j["pid"] = p.pid;
    j["name"] = p.name;
    j["nice"] = p.nice;
    j["uid"] = p.uid;
    j["cpuUsage"] = p.cpuUsage;
    j["parentPid"] = p.parentPid;
    j["isForeground"] = p.isForeground;
    j["memoryUsageKb"] = p.memoryUsageKb;
    j["cmdLine"] = p.cmdLine;
    j["state"] = p.state;
    j["threads"] = p.threads;
    j["startTime"] = p.startTime;
    j["elapsedTime"] = p.elapsedTime;
    j["residentSetSizeKb"] = p.residentSetSizeKb;
    j["virtualMemoryKb"] = p.virtualMemoryKb;
    j["cgroup"] = p.cgroup;
    j["executablePath"] = p.executablePath;
    return j;
}

std::vector<Proc> collectProcs() {
    std::vector<Proc> procs;
    std::vector<int> pids = listPids();
    procs.reserve(pids.size());

    for (int pid : pids) {
        try {
            procs.push_back(readProc(pid));
        } catch (...) {

        }
    }

    return procs;
}

std::string getSwapUsageBytes() {
    std::ifstream meminfo("/proc/meminfo");
    if (!meminfo.is_open()) {
        return "SWAP:0:0";
    }

    long swapTotalKB = 0;
    long swapFreeKB = 0;
    std::string line;

    while (std::getline(meminfo, line)) {
        std::istringstream iss(line);
        std::string key;
        long value;
        std::string unit;

        iss >> key >> value >> unit;

        if (key == "SwapTotal:") {
            swapTotalKB = value;
        } else if (key == "SwapFree:") {
            swapFreeKB = value;
        }

        if (swapTotalKB != 0 && swapFreeKB != 0) {
            break;
        }
    }

    long swapUsedBytes = (swapTotalKB - swapFreeKB) * 1024;
    long swapTotalBytes = swapTotalKB * 1024;

    return "SWAP:" + std::to_string(swapUsedBytes) + ":" + std::to_string(swapTotalBytes);
}

void processCommand(int sock, const std::string &received) {
    size_t colonPos = received.find(':');
    std::string cmd = received.substr(0, colonPos);


    if (cmd == "PING") {
        send_msg(sock, "PONG");
    } else if (cmd == "KILL") {
        int arg = -1;
        if (colonPos != std::string::npos) {
            try {
                arg = std::stoi(received.substr(colonPos + 1));
            } catch (...) {
                log_line("Invalid number in command");
            }
        }


        if (arg != 0) {
            killProcess(arg);
            send_msg(sock, "KILL_RESULT:true");
        } else {
            send_msg(sock, "KILL_RESULT:false");
            log_line("KILL command missing PID");
        }
    } else if(cmd == "FORCE_STOP"){
        std::string packageName = received.substr(colonPos+1);

        // Strict validation: only allow alphanumeric, dots, and underscores
        std::regex package_regex("^[a-zA-Z0-9._]+$");
        if (!std::regex_match(packageName, package_regex)) {
            send_msg(sock, "KILL_RESULT:false");
            log_line("Invalid package name: " + packageName);
            return;
        }

        // Length check
        if (packageName.empty() || packageName.length() > 255) {
            send_msg(sock, "KILL_RESULT:false");
            return;
        }

        // Additional safety: check for path traversal attempts
        if (packageName.find("..") != std::string::npos ||
            packageName.find("/") != std::string::npos) {
            send_msg(sock, "KILL_RESULT:false");
            log_line("Suspicious package name pattern");
            return;
        }

        // Now safe to use with system()
        std::string cmd = "am force-stop " + packageName;
        if (system(cmd.c_str()) == 0) {
            send_msg(sock, "KILL_RESULT:true");
        } else {
            send_msg(sock, "KILL_RESULT:false");
        }
    } else if (cmd == "KILL_GROUP") {
        int arg = -1;
        if (colonPos != std::string::npos) {
            try {
                arg = std::stoi(received.substr(colonPos + 1));
            } catch (...) {
                log_line("Invalid number in command");
            }
        }

        if (arg != 0) {
            killProcessGroup(arg);
            send_msg(sock, "KILL_RESULT:true");
        } else {
            log_line("KILL_GROUP command missing PGID");
            send_msg(sock, "KILL_RESULT:false");
        }
    } else if (cmd == "STOP_SELF" || cmd == "BUSY") {
        keep_running = 0;
    } else if (cmd == "LIST_PROCESS") {
        auto procs = collectProcs();
        nlohmann::json j = nlohmann::json::array();
        j.get_ref<nlohmann::json::array_t&>().reserve(procs.size());

        for (const auto &p : procs) {
            j.push_back(procToJson(p));  // No parse() needed!
        }

        std::string msg = j.dump(-1);
        if (!send_msg(sock, msg)) {
            log_line("Failed to send process list");
        }
    } else if (cmd == "CPU_PING") {
        int usage = calculateCpuUsage();
        std::string cpuUsage = "CPU:" + std::to_string(usage);
        log_line(cpuUsage);
        send_msg(sock, cpuUsage);
    } else if (cmd == "SWAP_PING") {
        std::string cpuUsage = getSwapUsageBytes();
        log_line(cpuUsage);
        send_msg(sock, cpuUsage);
    }if (cmd == "GPU_PING"){
        int usage = calculateGpuUsage();
        std::string gpuUsage = "GPU:" + std::to_string(usage);
        log_line(gpuUsage);
        send_msg(sock, gpuUsage);
    }else if (cmd == "CTEMP_PING"){
        int usage = getCpuTemperatureCelsius();
        std::string gpuUsage = "CTEMP:" + std::to_string(usage);
        log_line(gpuUsage);
        send_msg(sock, gpuUsage);
    }  else if (cmd == "PING_PID_CPU") {
        int arg = -1;
        if (colonPos != std::string::npos) {
            try {
                arg = std::stoi(received.substr(colonPos + 1));
            } catch (...) {
                log_line("Invalid number in command");
            }
        }

        std::string cpuUsage = "CPU_PID:" + std::to_string(calculateProcessCpuUsage(arg));
        log_line(cpuUsage);
        send_msg(sock, cpuUsage);
    } else {
        log_line("Unknown command: " + received);
    }
}

void daemonize()
{
    pid_t pid = fork();

    if (pid < 0) {
        exit(EXIT_FAILURE); // Fork failed
    }

    if (pid > 0) {
        exit(EXIT_SUCCESS); // Parent exits, child continues
    }

    // Child process becomes session leader
    if (setsid() < 0) {
        exit(EXIT_FAILURE);
    }

    // Catch signals (optional, to properly shut down)

    // Fork again to ensure the daemon cannot acquire a terminal
    pid = fork();
    if (pid < 0) {
        exit(EXIT_FAILURE);
    }
    if (pid > 0) {
        exit(EXIT_SUCCESS);
    }

    // Set file permissions mask
    umask(0);

    // Change working directory to root
    if (chdir("/") < 0) {
        exit(EXIT_FAILURE);
    }

    // Close standard file descriptors
    close(STDIN_FILENO);
    close(STDOUT_FILENO);
    close(STDERR_FILENO);

    // Optionally redirect fds to /dev/null
    open("/dev/null", O_RDONLY); // stdin
    open("/dev/null", O_RDWR);   // stdout
    open("/dev/null", O_RDWR);   // stderr
}

int main(int argc, char* argv[]) {
    bool dFlag = false;
    int port = -1;

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];

        if (arg == "-D") {
            dFlag = true;
        }
        else if (arg == "-p") {
            if (i + 1 < argc) {
                port = std::atoi(argv[i + 1]); // convert next arg to int
                i++; // skip next argument, since it's the port number
            } else {
                std::cerr << "ERROR: -p requires a port number" << std::endl;
                return 1;
            }
        }
        else {
            std::cerr << "Unknown argument: " << arg << std::endl;
        }
    }

    std::cout << "dFlag: " << (dFlag ? "true" : "false") << "\n";
    std::cout << "Port: " << port << "\n";


    if (port == -1) {
        std::cerr << "ERROR: Port number must be specified with -p" << std::endl;
        return 1;
    }

    if (port <= 0){
        std::cerr << "Invalid port received: " << port << std::endl;
    }


    log_line("=== Client starting ===");

    // Setup signal handlers
    signal(SIGINT, handle_sigint);
    signal(SIGTERM, handle_sigint);

    // --- Create socket ---
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        log_line(std::string("ERROR: failed to create socket: ") + strerror(errno));
        return 1;
    }

    // Optional: make non-blocking
    int flags = fcntl(sock, F_GETFL, 0);
    fcntl(sock, F_SETFL, flags | O_NONBLOCK);

    // --- Setup TCP socket address ---
    struct sockaddr_in addr{};
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port); // Use the port variable

    // Connect to localhost only (127.0.0.1)
    if (inet_pton(AF_INET, "127.0.0.1", &addr.sin_addr) <= 0) {
        log_line("ERROR: Invalid address");
        close(sock);
        return 1;
    }

    log_line("Connecting to server on 127.0.0.1:" + std::to_string(port) + "...");

    // --- Connect ---
    if (connect(sock, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        if (errno != EINPROGRESS) { // EINPROGRESS is okay for non-blocking
            log_line(std::string("ERROR: could not connect to daemon: ") + strerror(errno));
            close(sock);
            return 1;
        }
    }

    // Optional: set socket back to blocking after connection
    fcntl(sock, F_SETFL, flags);

    log_line("Connected to server.");

    // Set non-blocking again if needed
    fcntl(sock, F_SETFL, flags | O_NONBLOCK);

    // Log process info
    pid_t pid = getpid();
    uid_t uid = getuid();
    struct passwd *pw = getpwuid(uid);
    const char *username = pw ? pw->pw_name : "unknown";
    log_line("PID: " + std::to_string(pid) +
             " UID: " + std::to_string(uid) +
             " USER: " + std::string(username));


    // --- Use epoll for efficient I/O ---
    int epoll_fd = epoll_create1(0);
    if (epoll_fd < 0) {
        log_line("ERROR: Failed to create epoll instance");
        close(sock);
        return 1;
    }

    struct epoll_event ev;
    ev.events = EPOLLIN | EPOLLET; // Edge-triggered
    ev.data.fd = sock;

    if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, sock, &ev) < 0) {
        log_line("ERROR: Failed to add socket to epoll");
        close(epoll_fd);
        close(sock);
        return 1;
    }

    const size_t BUF_SIZE = 4096; // Reduced buffer size
    std::unique_ptr<char[]> buf(new char[BUF_SIZE]);
    std::string recv_buffer; // Accumulate partial messages

    struct epoll_event events[1];

    if (dFlag) {
        daemonize();
    }

    while (keep_running) {
        // Wait for events with timeout (reduces CPU usage when idle)
        int n = epoll_wait(epoll_fd, events, 1, 1000); // 1 second timeout

        if (n < 0) {
            if (errno == EINTR) continue;
            log_line("ERROR: epoll_wait failed: " + std::string(strerror(errno)));
            break;
        }

        if (n == 0) continue; // Timeout, no data

        // Log what events we got for debugging
        uint32_t ev_flags = events[0].events;
        log_line("Epoll event: EPOLLIN=" + std::to_string(!!(ev_flags & EPOLLIN)) +
                 " EPOLLOUT=" + std::to_string(!!(ev_flags & EPOLLOUT)) +
                 " EPOLLERR=" + std::to_string(!!(ev_flags & EPOLLERR)) +
                 " EPOLLHUP=" + std::to_string(!!(ev_flags & EPOLLHUP)));

        // Check for errors first
        if (ev_flags & (EPOLLERR | EPOLLHUP)) {
            int socket_error = 0;
            socklen_t len = sizeof(socket_error);
            if (getsockopt(sock, SOL_SOCKET, SO_ERROR, &socket_error, &len) == 0) {
                if (socket_error != 0) {
                    log_line("Socket error detected: " + std::string(strerror(socket_error)));
                }
            }

            if (ev_flags & EPOLLHUP) {
                log_line("Socket hangup detected");
            }

            // Only exit if it's an error, not just a hangup with data available
            if ((ev_flags & EPOLLERR) || ((ev_flags & EPOLLHUP) && !(ev_flags & EPOLLIN))) {
                keep_running = 0;
                break;
            }
        }

        // Read available data
        if (ev_flags & EPOLLIN) {
            while (true) {
                ssize_t r = recv(sock, buf.get(), BUF_SIZE - 1, 0);

                if (r > 0) {
                    buf[r] = '\0';
                    recv_buffer.append(buf.get(), r);

                    // Process complete messages (lines)
                    size_t pos;
                    while ((pos = recv_buffer.find('\n')) != std::string::npos) {
                        std::string message = recv_buffer.substr(0, pos);
                        recv_buffer.erase(0, pos + 1);

                        // Trim whitespace
                        message.erase(message.find_last_not_of(" \r\n\t") + 1);

                        if (!message.empty()) {
                            log_line("Received: " + message);
                            processCommand(sock, message);
                        }
                    }
                } else if (r == 0) {
                    log_line("Connection closed by server. Exiting.");
                    keep_running = 0;
                    break;
                } else {
                    int err = errno;
                    if (err == EAGAIN || err == EWOULDBLOCK) {
                        // No more data available - this is normal for non-blocking
                        break;
                    } else if (err == EINTR) {
                        // Interrupted, try again
                        continue;
                    } else {
                        log_line("Connection error: " + std::string(strerror(err)) +
                                 " (errno=" + std::to_string(err) + ")");
                        keep_running = 0;
                        break;
                    }
                }
            }
        }
    }

    // Cleanup
    close(epoll_fd);
    close(sock);
    log_line("Client shutdown complete.");

    return 0;
}