#include <arpa/inet.h>
#include <cerrno>
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <fcntl.h>
#include <climits>
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
using json = nlohmann::json;

static std::string toLower(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(),
                   [](unsigned char c){ return std::tolower(c); });
    return s;
}

static bool isCpuThermalType(const std::string& type) {
    std::string t = toLower(type);
    return (t.find("cpu") != std::string::npos) ||
           (t.find("soc") != std::string::npos) ||
           (t.find("ap")  != std::string::npos) ||
           (t.find("cluster") != std::string::npos);
}

int getCpuTemperatureCelsius() {
    const std::string basePath = "/sys/class/thermal/";
    DIR* dir = opendir(basePath.c_str());
    if (!dir) return -1;

    struct dirent* entry;
    int maxTemp = -1;

    while ((entry = readdir(dir)) != nullptr) {
        std::string name = entry->d_name;
        if (name.find("thermal_zone") == std::string::npos) continue;

        std::string zonePath = basePath + name;
        std::ifstream typeFile(zonePath + "/type");
        if (!typeFile.is_open()) continue;

        std::string type;
        std::getline(typeFile, type);
        typeFile.close();

        if (!isCpuThermalType(type)) continue;

        std::ifstream tempFile(zonePath + "/temp");
        if (!tempFile.is_open()) continue;

        long raw = 0;
        tempFile >> raw;
        tempFile.close();

        if (raw <= 0) continue;

        int tempC = (raw > 1000) ? static_cast<int>(raw / 1000) : static_cast<int>(raw);
        if (tempC >= 5 && tempC <= 100) {
            maxTemp = std::max(maxTemp, tempC);
        }
    }

    closedir(dir);
    return maxTemp;
}

std::optional<int> getBatteryCycleCount() {
    static const std::vector<std::string> paths = {
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/bms/cycle_count",
            "/sys/class/power_supply/Battery/cycle_count",
    };

    for (const auto& path : paths) {
        std::ifstream file(path);
        if (!file.is_open()) continue;

        std::string content;
        std::getline(file, content);

        try {
            return std::stoi(content);
        } catch (...) {
            continue;
        }
    }

    return std::nullopt;
}

static std::regex pid_regex("\\d+");

std::vector<int> listPids() {
    std::vector<int> pids;
    pids.reserve(256);
    for (const auto &entry : fs::directory_iterator("/proc")) {
        try {
            if (entry.is_directory()) {
                std::string name = entry.path().filename();
                if (std::regex_match(name, pid_regex)) {
                    pids.push_back(std::stoi(name));
                }
            }
        } catch (...) {}
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

bool send_json(int sock, const json &j) {
    return send_msg(sock, j.dump());
}

struct CpuStat {
    long user, nice, system, idle, iowait, irq, softirq, steal;
    long total() const { return user + nice + system + idle + iowait + irq + softirq + steal; }
    long active() const { return total() - idle; }
};

CpuStat readCpuStat() {
    std::ifstream file("/proc/stat");
    if (!file.is_open()) return {0,0,0,0,0,0,0,0};
    std::string line;
    std::getline(file, line);
    if (line.rfind("cpu ", 0) == 0) {
        std::istringstream iss(line);
        std::string cpuLabel;
        long v[8] = {0};
        iss >> cpuLabel;
        for (int i = 0; i < 8; ++i) if (!(iss >> v[i])) break;
        return {v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]};
    }
    return {0,0,0,0,0,0,0,0};
}

int calculateCpuUsage() {
    CpuStat prev = readCpuStat();
    std::this_thread::sleep_for(std::chrono::milliseconds(100));
    CpuStat curr = readCpuStat();
    uint64_t totalDiff = curr.total() - prev.total();
    uint64_t activeDiff = curr.active() - prev.active();
    if (totalDiff == 0) return 0;
    double usage = (double)activeDiff / (double)totalDiff * 100.0;
    return std::clamp((int)usage, 0, 100);
}

int readInt(const char* path) {
    std::ifstream file(path);
    int value = -1;
    if (file.is_open()) { file >> value; file.close(); }
    return value;
}

int calculateGpuUsage() {
    int usage = readInt("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage");
    if (usage >= 0) return usage;
    usage = readInt("/sys/class/misc/mali0/device/utilization");
    if (usage >= 0) return usage;
    return -1;
}

bool killProcess(int pid) {
    if (kill(pid, SIGKILL) == 0) return true;
    std::cerr << "Failed to kill process " << pid << ": " << strerror(errno) << std::endl;
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
    if (uptime.is_open()) uptime >> uptimeSeconds;
    return static_cast<long>(uptimeSeconds * sysconf(_SC_CLK_TCK));
}

float calculateProcessCpuUsage(int pid) {
    std::string statPath = "/proc/" + std::to_string(pid) + "/stat";
    std::ifstream statFile(statPath);
    if (!statFile.is_open()) return 0.0f;
    std::string line;
    std::getline(statFile, line);
    size_t lastParen = line.rfind(')');
    if (lastParen == std::string::npos) return 0.0f;
    std::istringstream iss(line.substr(lastParen + 2));
    std::string state;
    long utime = 0, stime = 0, starttime = 0;
    for (int i = 0; i < 11; ++i) { std::string dummy; iss >> dummy; }
    iss >> utime >> stime;
    for (int i = 0; i < 6; ++i) { std::string dummy; iss >> dummy; }
    iss >> starttime;
    long totalTime = utime + stime;
    long uptime = getSystemUptime();
    long elapsedTime = uptime - starttime;
    if (elapsedTime > 0) return (100.0f * totalTime) / elapsedTime;
    return 0.0f;
}

bool isForegroundProcess(int pid) {
    std::string oomPath = "/proc/" + std::to_string(pid) + "/oom_score_adj";
    std::ifstream oomFile(oomPath);
    if (!oomFile.is_open()) return false;
    int oomScore = 0;
    oomFile >> oomScore;
    return oomScore <= 100;
}

std::string getCgroup(int pid) {
    std::string cgroupPath = "/proc/" + std::to_string(pid) + "/cgroup";
    std::ifstream cgroupFile(cgroupPath);
    if (!cgroupFile.is_open()) return "";
    std::string line;
    if (std::getline(cgroupFile, line)) {
        size_t colonPos = line.find_last_of(':');
        if (colonPos != std::string::npos) return line.substr(colonPos + 1);
    }
    return line;
}

std::string getExecutablePath(int pid) {
    std::string exePath = "/proc/" + std::to_string(pid) + "/exe";
    char path[PATH_MAX];
    ssize_t len = readlink(exePath.c_str(), path, sizeof(path) - 1);
    if (len != -1) { path[len] = '\0'; return std::string(path); }
    return "";
}

Proc readProc(int pid) {
    Proc p{}; p.pid = pid;
    std::string procPath = "/proc/" + std::to_string(pid);
    std::ifstream commFile(procPath + "/comm");
    if (commFile.is_open()) std::getline(commFile, p.name);
    std::ifstream cmdFile(procPath + "/cmdline", std::ios::binary);
    if (cmdFile.is_open()) std::getline(cmdFile, p.cmdLine, '\0');
    std::ifstream statFile(procPath + "/stat");
    if (statFile.is_open()) {
        std::string line; std::getline(statFile, line);
        size_t lastParen = line.rfind(')');
        if (lastParen != std::string::npos) {
            std::istringstream iss(line.substr(lastParen + 2));
            std::string dummy;
            for (int i = 0; i < 6; ++i) iss >> dummy;
            for (int i = 0; i < 8; ++i) iss >> dummy;
            iss >> dummy; // priority
            iss >> p.nice;
            iss >> dummy >> dummy;
            iss >> p.startTime;
        }
    }
    long uptime = getSystemUptime();
    p.elapsedTime = static_cast<float>(uptime - p.startTime) / sysconf(_SC_CLK_TCK);
    std::ifstream statusFile(procPath + "/status");
    std::string line;
    int fieldsFound = 0;
    while (fieldsFound < 6 && std::getline(statusFile, line)) {
        if (line.compare(0, 4, "Uid:") == 0) { p.uid = std::stoi(line.substr(5)); fieldsFound++; }
        else if (line.compare(0, 5, "PPid:") == 0) { p.parentPid = std::stoi(line.substr(6)); fieldsFound++; }
        else if (line.compare(0, 6, "VmRSS:") == 0) { p.residentSetSizeKb = std::stol(line.substr(7)); p.memoryUsageKb = p.residentSetSizeKb; fieldsFound++; }
        else if (line.compare(0, 7, "VmSize:") == 0) { p.virtualMemoryKb = std::stol(line.substr(8)); fieldsFound++; }
        else if (line.compare(0, 8, "Threads:") == 0) { p.threads = std::stoi(line.substr(9)); fieldsFound++; }
        else if (line.compare(0, 6, "State:") == 0) { p.state = line.substr(7); fieldsFound++; }
    }
    p.cpuUsage = calculateProcessCpuUsage(pid);
    p.isForeground = isForegroundProcess(pid);
    p.cgroup = getCgroup(pid);
    p.executablePath = getExecutablePath(pid);
    return p;
}

json procToJson(const Proc &p) {
    return {
        {"pid", p.pid}, {"name", p.name}, {"nice", p.nice}, {"uid", p.uid},
        {"cpuUsage", p.cpuUsage}, {"parentPid", p.parentPid}, {"isForeground", p.isForeground},
        {"memoryUsageKb", p.memoryUsageKb}, {"cmdLine", p.cmdLine}, {"state", p.state},
        {"threads", p.threads}, {"startTime", p.startTime}, {"elapsedTime", p.elapsedTime},
        {"residentSetSizeKb", p.residentSetSizeKb}, {"virtualMemoryKb", p.virtualMemoryKb},
        {"cgroup", p.cgroup}, {"executablePath", p.executablePath}
    };
}

std::vector<Proc> collectProcs() {
    std::vector<Proc> procs;
    std::vector<int> pids = listPids();
    procs.reserve(pids.size());
    for (int pid : pids) { try { procs.push_back(readProc(pid)); } catch (...) {} }
    return procs;
}

void getSwapUsage(long &used, long &total) {
    used = 0; total = 0;
    std::ifstream meminfo("/proc/meminfo");
    if (!meminfo.is_open()) return;
    long totalKB = 0, freeKB = 0;
    std::string line;
    while (std::getline(meminfo, line)) {
        if (line.compare(0, 10, "SwapTotal:") == 0) totalKB = std::stol(line.substr(10));
        else if (line.compare(0, 9, "SwapFree:") == 0) freeKB = std::stol(line.substr(9));
    }
    used = (totalKB - freeKB) * 1024;
    total = totalKB * 1024;
}

struct DiskInfo {
    std::string name;
    std::string model;
    long long sizeBytes;
    bool isRemovable;
};

std::vector<DiskInfo> listDisks() {
    std::vector<DiskInfo> disks;
    const std::string path = "/sys/class/block/";
    if (!fs::exists(path)) return disks;

    for (const auto& entry : fs::directory_iterator(path)) {
        std::string name = entry.path().filename().string();

        // Fast string filters first — no syscalls
        if (name.find("loop") == 0 || name.find("ram") == 0 ||
            name.find("zram") == 0 || name.find("dm-") == 0 ||
            name.find("vd") == 0 ||
            name.find("rpmb") != std::string::npos ||
            name.find("boot") != std::string::npos) continue;

        // Use symlink target to detect partitions — no extra stat() call
        // Partitions resolve to: .../block/sda/sda1  (parent dir = disk name)
        // Whole disks resolve to: .../block/sda
        std::error_code ec;
        fs::path target = fs::read_symlink(entry.path(), ec);
        if (!ec) {
            // If the parent directory name matches a disk name, it's a partition
            std::string parentName = target.parent_path().filename().string();
            if (parentName != "block" && !parentName.empty() &&
                parentName.find_first_of("0123456789") == std::string::npos == false) {
                // parent is like "sda", meaning this entry is "sda1" → skip
                continue;
            }
        }

        // Now read files only for candidates that passed all filters
        DiskInfo info;
        info.name = name;

        std::ifstream sizeFile(entry.path() / "size");
        long long sectors = 0;
        if (sizeFile >> sectors) info.sizeBytes = sectors * 512;
        else info.sizeBytes = 0;

        if (info.sizeBytes <= 100LL * 1024 * 1024) continue; // skip early

        std::ifstream modelFile(entry.path() / "device/model");
        if (modelFile) std::getline(modelFile, info.model);
        else {
            std::ifstream nameFile(entry.path() / "device/name");
            if (nameFile) std::getline(nameFile, info.model);
            else info.model = name;
        }
        info.model.erase(std::find_if(info.model.rbegin(), info.model.rend(),
                                      [](unsigned char c) { return !std::isspace(c); }).base(), info.model.end());

        std::ifstream removableFile(entry.path() / "removable");
        int removable = 0;
        removableFile >> removable;
        info.isRemovable = (removable == 1);

        disks.push_back(info);
    }
    return disks;
}

struct DiskStat {
    unsigned long long readBytes;
    unsigned long long writeBytes;
};

DiskStat getDiskStat(const std::string& diskName) {
    std::ifstream diskstats("/proc/diskstats");
    std::string line;
    while (std::getline(diskstats, line)) {
        std::istringstream iss(line);
        int major, minor;
        std::string name;
        unsigned long long reads, readsMerged, readSectors, readTime;
        unsigned long long writes, writesMerged, writeSectors, writeTime;
        if (iss >> major >> minor >> name >> reads >> readsMerged >> readSectors >> readTime >> writes >> writesMerged >> writeSectors >> writeTime) {
            if (name == diskName) return {readSectors * 512, writeSectors * 512};
        }
    }
    return {0, 0};
}

struct NetStat {
    unsigned long long rxBytes;
    unsigned long long txBytes;
};

std::vector<std::string> listNetInterfaces() {
    std::vector<std::string> interfaces;
    std::ifstream netdev("/proc/net/dev");
    std::string line;
    std::getline(netdev, line);
    std::getline(netdev, line);
    while (std::getline(netdev, line)) {
        size_t colon = line.find(':');
        if (colon != std::string::npos) {
            std::string name = line.substr(0, colon);
            name.erase(0, name.find_first_not_of(' '));
            if (name != "lo") interfaces.push_back(name);
        }
    }
    return interfaces;
}

NetStat getNetStat(const std::string& iface) {
    std::ifstream netdev("/proc/net/dev");
    std::string line;
    while (std::getline(netdev, line)) {
        if (line.find(iface + ":") != std::string::npos) {
            std::istringstream iss(line.substr(line.find(':') + 1));
            unsigned long long rxBytes, dummy;
            unsigned long long txBytes;
            iss >> rxBytes;
            for(int i=0; i<7; ++i) iss >> dummy;
            iss >> txBytes;
            return {rxBytes, txBytes};
        }
    }
    return {0, 0};
}

struct NetStatSnapshot {
    unsigned long long rxBytes;
    unsigned long long txBytes;
    std::chrono::steady_clock::time_point timestamp;
};

static std::unordered_map<std::string, NetStatSnapshot> netStatCache;


void processCommand(int sock, const std::string &received) {
    try {
        json j_in = json::parse(received);
        std::string cmd = j_in.value("cmd", "");
        json j_out;

        if (cmd == "PING") {
            j_out["type"] = "PONG";
            send_json(sock, j_out);
        } else if (cmd == "KILL") {
            int pid = j_in.value("pid", -1);
            bool success = (pid > 0) && killProcess(pid);
            j_out["type"] = "KILL_RESULT";
            j_out["success"] = success;
            send_json(sock, j_out);
        } else if (cmd == "FORCE_STOP") {
            std::string pkg = j_in.value("pkg", "");
            std::regex pkg_regex("^[a-zA-Z0-9._]+$");
            bool success = false;
            if (std::regex_match(pkg, pkg_regex) && pkg.length() <= 255) {
                std::string scmd = "am force-stop " + pkg;
                success = (system(scmd.c_str()) == 0);
            }
            j_out["type"] = "KILL_RESULT";
            j_out["success"] = success;
            send_json(sock, j_out);
        } else if (cmd == "KILL_GROUP") {
            int pgid = j_in.value("pgid", -1);
            bool success = (pgid > 0) ? killProcessGroup(pgid) : false;
            j_out["type"] = "KILL_RESULT";
            j_out["success"] = success;
            send_json(sock, j_out);
        } else if (cmd == "STOP_SELF" || cmd == "BUSY") {
            keep_running = 0;
        } else if (cmd == "LIST_PROCESS") {
            auto procs = collectProcs();
            json procs_j = json::array();
            for (const auto &p : procs) procs_j.push_back(procToJson(p));
            j_out["type"] = "PROCESS_LIST";
            j_out["processes"] = procs_j;
            send_json(sock, j_out);
        } else if (cmd == "CPU_PING") {
            j_out["type"] = "CPU_USAGE";
            j_out["usage"] = calculateCpuUsage();
            send_json(sock, j_out);
        } else if (cmd == "SWAP_PING") {
            long used, total;
            getSwapUsage(used, total);
            j_out["type"] = "SWAP_USAGE";
            j_out["used"] = used;
            j_out["total"] = total;
            send_json(sock, j_out);
        } else if (cmd == "GPU_PING") {
            j_out["type"] = "GPU_USAGE";
            j_out["usage"] = calculateGpuUsage();
            send_json(sock, j_out);
        } else if (cmd == "CTEMP_PING") {
            j_out["type"] = "CPU_TEMP";
            j_out["temp"] = getCpuTemperatureCelsius();
            send_json(sock, j_out);
        } else if (cmd == "PING_PID_CPU") {
            int pid = j_in.value("pid", -1);
            j_out["type"] = "PROCESS_CPU_USAGE";
            j_out["usage"] = calculateProcessCpuUsage(pid);
            send_json(sock, j_out);
        } else if(cmd == "BAT_CHARGE_CYCLES"){
            j_out["type"] = "CHARGE_CYCLES";
            j_out["cycles"] = getBatteryCycleCount().value_or(-1);
            send_json(sock,j_out);
        } else if (cmd == "LIST_DISKS") {
            auto disks = listDisks();
            json disks_j = json::array();
            for (const auto& d : disks) {
                disks_j.push_back({
                    {"name", d.name}, {"model", d.model}, {"sizeBytes", d.sizeBytes}, {"isRemovable", d.isRemovable}
                });
            }
            j_out["type"] = "DISK_LIST";
            j_out["disks"] = disks_j;
            send_json(sock, j_out);
        } else if (cmd == "DISK_PING") {
            std::string diskName = j_in.value("disk", "");
            auto stat = getDiskStat(diskName);
            j_out["type"] = "DISK_STATS";
            j_out["readBytes"] = stat.readBytes;
            j_out["writeBytes"] = stat.writeBytes;
            send_json(sock, j_out);
        } else if (cmd == "LIST_NET_INTERFACES") {
            j_out["type"] = "NET_INTERFACE_LIST";
            j_out["interfaces"] = listNetInterfaces();
            send_json(sock, j_out);
        } else if (cmd == "NET_PING") {
            std::string iface = j_in.value("interface", "");
            auto now = std::chrono::steady_clock::now();
            auto curr = getNetStat(iface);

            j_out["type"] = "NET_STATS";

            auto it = netStatCache.find(iface);
            if (it != netStatCache.end()) {
                auto& prev = it->second;
                double elapsed = std::chrono::duration<double>(now - prev.timestamp).count();

                if (elapsed > 0.0) {
                    j_out["rxBytesPerSec"] = (curr.rxBytes - prev.rxBytes) / elapsed;
                    j_out["txBytesPerSec"] = (curr.txBytes - prev.txBytes) / elapsed;
                } else {
                    j_out["rxBytesPerSec"] = 0;
                    j_out["txBytesPerSec"] = 0;
                }
            } else {
                // First call — no delta yet
                j_out["rxBytesPerSec"] = 0;
                j_out["txBytesPerSec"] = 0;
            }

            // Update cache
            netStatCache[iface] = {curr.rxBytes, curr.txBytes, now};

            j_out["rxBytes"] = curr.rxBytes; // keep cumulative if needed
            j_out["txBytes"] = curr.txBytes;
            send_json(sock, j_out);
        } else {
            log_line("Unknown command: " + cmd);
        }
    } catch (const std::exception& e) {
        log_line("JSON parse error: " + std::string(e.what()) + " | Data: " + received);
    }
}

void daemonize() {
    pid_t pid = fork();
    if (pid < 0) exit(EXIT_FAILURE);
    if (pid > 0) exit(EXIT_SUCCESS);
    if (setsid() < 0) exit(EXIT_FAILURE);
    pid = fork();
    if (pid < 0) exit(EXIT_FAILURE);
    if (pid > 0) exit(EXIT_SUCCESS);
    umask(0);
    if (chdir("/") < 0) exit(EXIT_FAILURE);
    close(STDIN_FILENO); close(STDOUT_FILENO); close(STDERR_FILENO);
    open("/dev/null", O_RDONLY); open("/dev/null", O_RDWR); open("/dev/null", O_RDWR);
}

int main(int argc, char* argv[]) {
    bool dFlag = false;
    int port = -1;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "-D") dFlag = true;
        else if (arg == "-p" && i + 1 < argc) port = std::atoi(argv[++i]);
    }
    if (port <= 0) { std::cerr << "ERROR: Valid port must be specified with -p" << std::endl; return 1; }

    signal(SIGINT, handle_sigint);
    signal(SIGTERM, handle_sigint);

    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) { log_line("ERROR: socket creation failed"); return 1; }

    int flags = fcntl(sock, F_GETFL, 0);
    fcntl(sock, F_SETFL, flags | O_NONBLOCK);

    struct sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    inet_pton(AF_INET, "127.0.0.1", &addr.sin_addr);

    if (connect(sock, (struct sockaddr *)&addr, sizeof(addr)) < 0 && errno != EINPROGRESS) {
        log_line("ERROR: connection failed: " + std::string(strerror(errno)));
        close(sock); return 1;
    }

    fcntl(sock, F_SETFL, flags);
    log_line("Connected to server.");
    fcntl(sock, F_SETFL, flags | O_NONBLOCK);

    int epoll_fd = epoll_create1(0);
    struct epoll_event ev;
    ev.events = EPOLLIN | EPOLLET;
    ev.data.fd = sock;
    epoll_ctl(epoll_fd, EPOLL_CTL_ADD, sock, &ev);

    const size_t BUF_SIZE = 8192;
    std::unique_ptr<char[]> buf(new char[BUF_SIZE]);
    std::string recv_buffer;
    struct epoll_event events[1];

    if (dFlag) daemonize();

    while (keep_running) {
        int n = epoll_wait(epoll_fd, events, 1, 1000);
        if (n < 0 && errno != EINTR) break;
        if (n <= 0) continue;

        if (events[0].events & (EPOLLERR | EPOLLHUP)) {
            if (!(events[0].events & EPOLLIN)) { keep_running = 0; break; }
        }

        if (events[0].events & EPOLLIN) {
            while (true) {
                ssize_t r = recv(sock, buf.get(), BUF_SIZE - 1, 0);
                if (r > 0) {
                    buf[r] = '\0';
                    recv_buffer.append(buf.get(), r);
                    size_t pos;
                    while ((pos = recv_buffer.find('\n')) != std::string::npos) {
                        std::string message = recv_buffer.substr(0, pos);
                        recv_buffer.erase(0, pos + 1);
                        if (!message.empty()) processCommand(sock, message);
                    }
                } else if (r == 0) { keep_running = 0; break; }
                else { if (errno != EAGAIN && errno != EWOULDBLOCK) keep_running = 0; break; }
            }
        }
    }

    close(epoll_fd); close(sock);
    return 0;
}
