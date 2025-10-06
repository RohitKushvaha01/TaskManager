#include <arpa/inet.h>
#include <cerrno>
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <fcntl.h>
#include <iostream>
#include <memory>
#include <pwd.h>
#include <string>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>
#include <unistd.h>
#include <chrono>
#include <thread>
#include "json.hpp"
#include <filesystem>
#include <vector>
#include <fstream>
#include <sstream>
#include <iostream>
#include <regex>
#include <sys/epoll.h>
#include <limits.h>

namespace fs = std::filesystem;

// Cache regex for better performance
static std::regex pid_regex("\\d+");

std::vector<int> listPids() {
    std::vector<int> pids;
    pids.reserve(256); // Reserve space to avoid reallocations

    try {
        for (const auto &entry : fs::directory_iterator("/proc")) {
            if (entry.is_directory()) {
                std::string name = entry.path().filename();
                if (std::regex_match(name, pid_regex)) {
                    pids.push_back(std::stoi(name));
                }
            }
        }
    } catch (...) {
        // Handle filesystem errors gracefully
    }
    return pids;
}

static volatile sig_atomic_t keep_running = 1;
static const char *abstract_name = "\0TaskmanagerD";

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

// Optimized: Read only first line, don't iterate through entire file
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

// Optimized: Reduced sleep time for faster response
int calculateCpuUsage() {
    CpuStat prev = readCpuStat();
    std::this_thread::sleep_for(std::chrono::milliseconds(200));
    CpuStat curr = readCpuStat();

    uint64_t totalDiff = curr.total() - prev.total();
    uint64_t activeDiff = curr.active() - prev.active();

    if (totalDiff == 0) return 0;

    double usage = (double)activeDiff / (double)totalDiff * 100.0;
    if (usage < 0) usage = 0;
    if (usage > 100) usage = 100;
    return (int)usage;
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

// Helper to get system uptime in clock ticks
long getSystemUptime() {
    std::ifstream uptime("/proc/uptime");
    double uptimeSeconds = 0.0;
    if (uptime.is_open()) {
        uptime >> uptimeSeconds;
    }
    return static_cast<long>(uptimeSeconds * sysconf(_SC_CLK_TCK));
}

// Helper to calculate CPU usage for a process
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

    // Skip to field 22 (starttime)
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

// Check if process is foreground (checking OOM score adjust)
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

// Get cgroup info
std::string getCgroup(int pid) {
    std::string cgroupPath = "/proc/" + std::to_string(pid) + "/cgroup";
    std::ifstream cgroupFile(cgroupPath);

    if (!cgroupFile.is_open()) return "";

    std::string line;
    // Get first line (usually the most relevant)
    if (std::getline(cgroupFile, line)) {
        size_t colonPos = line.find_last_of(':');
        if (colonPos != std::string::npos) {
            return line.substr(colonPos + 1);
        }
    }
    return line;
}

// Get executable path
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

    // Read name
    std::ifstream commFile(procPath + "/comm");
    if (commFile.is_open()) {
        std::getline(commFile, p.name);
    }

    // Read cmdline
    std::ifstream cmdFile(procPath + "/cmdline", std::ios::binary);
    if (cmdFile.is_open()) {
        std::getline(cmdFile, p.cmdLine, '\0');
    }

    // Read /proc/<pid>/stat for timing and nice value
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

    // Calculate elapsed time
    long uptime = getSystemUptime();
    p.elapsedTime = static_cast<float>(uptime - p.startTime) / sysconf(_SC_CLK_TCK);

    // Read /proc/<pid>/status
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

    // Calculate CPU usage
    p.cpuUsage = calculateProcessCpuUsage(pid);

    // Check if foreground
    p.isForeground = isForegroundProcess(pid);

    // Get cgroup
    p.cgroup = getCgroup(pid);

    // Get executable path
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
            // Skip processes that disappeared or can't be read
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
            break; // got both values
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
        int arg;
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
        std::string cmd = "am force-stop " + received.substr(colonPos+1);
        if (system(cmd.c_str()) == 0) {
            send_msg(sock, "KILL_RESULT:true");
        } else {
            send_msg(sock, "KILL_RESULT:false");
        }
    } else if (cmd == "KILL_GROUP") {
        int arg;
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
    }  else if (cmd == "PING_PID_CPU") {
        int arg;
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

int main() {
    std::thread compileThread([]() {
        system("pm compile -m speed com.rk.taskmanager");
        system("pm compile -r bg-dexopt com.rk.taskmanager");
    });

    compileThread.detach();

    daemonize();


    log_line("=== Client starting ===");

    // Setup signal handler
    signal(SIGINT, handle_sigint);
    signal(SIGTERM, handle_sigint);

    // --- Connect to server ---
    int sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (sock < 0) {
        log_line(std::string("ERROR: failed to create socket: ") + strerror(errno));
        return 1;
    }

    // Set socket to non-blocking for better responsiveness
    int flags = fcntl(sock, F_GETFL, 0);
    fcntl(sock, F_SETFL, flags | O_NONBLOCK);

    struct sockaddr_un addr{};
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    memcpy(addr.sun_path, abstract_name, 1 + strlen(abstract_name + 1));

    socklen_t addrlen = offsetof(struct sockaddr_un, sun_path) + 1 + strlen(abstract_name + 1);

    log_line("Connecting to server...");

    // Retry connection with non-blocking socket
    fcntl(sock, F_SETFL, flags); // Set back to blocking for connect
    if (connect(sock, (struct sockaddr *)&addr, addrlen) < 0) {
        log_line(std::string("ERROR: could not connect to daemon: ") + strerror(errno));
        close(sock);
        return 1;
    }

    // Set non-blocking after connection
    fcntl(sock, F_SETFL, flags | O_NONBLOCK);
    log_line("Connected to server.");

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

    while (keep_running) {
        // Wait for events with timeout (reduces CPU usage when idle)
        int n = epoll_wait(epoll_fd, events, 1, 1000); // 1 second timeout

        if (n < 0) {
            if (errno == EINTR) continue;
            log_line("ERROR: epoll_wait failed");
            break;
        }

        if (n == 0) continue; // Timeout, no data

        // Data available
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
                if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    // No more data available
                    break;
                } else if (errno != EINTR) {
                    log_line("Connection error. Exiting.");
                    keep_running = 0;
                    break;
                }
            }
        }
    }

    log_line("=== Client exiting ===");
    close(epoll_fd);
    close(sock);
    return 0;
}