#include <cerrno>
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <fcntl.h>
#include <climits>
#include <pwd.h>
#include <sys/stat.h>
#include <sys/types.h>
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
    std::string msg = "[" + now_str() + "] " + line + "\n";
    write(STDERR_FILENO, msg.c_str(), msg.size());
}

bool send_msg(const std::string &msg) {
    std::string data = msg + "\n";
    size_t total = 0;
    while (total < data.size()) {
        ssize_t written = write(STDOUT_FILENO, data.data() + total, data.size() - total);
        if (written <= 0) return false;
        total += written;
    }
    return true;
}

bool send_json(const json &j) {
    return send_msg(j.dump());
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
            for (int i = 0; i < 9; ++i) iss >> dummy;
            iss >> dummy;
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

struct NetStat {
    unsigned long long rxBytes;
    unsigned long long txBytes;
};

struct NetInterfaceInfo {
    std::string name;
    unsigned long long totalBytes;
};

std::vector<NetInterfaceInfo> listNetInterfaces() {
    std::vector<NetInterfaceInfo> interfaces;
    std::ifstream netdev("/proc/net/dev");
    std::string line;
    if (!netdev.is_open()) return interfaces;

    std::getline(netdev, line);
    std::getline(netdev, line);
    while (std::getline(netdev, line)) {
        size_t colon = line.find(':');
        if (colon != std::string::npos) {
            std::string name = line.substr(0, colon);
            name.erase(0, name.find_first_not_of(' '));
            if (name == "lo") continue;

            std::istringstream iss(line.substr(colon + 1));
            unsigned long long rxBytes, txBytes, dummy;
            iss >> rxBytes;
            for(int i=0; i<7; ++i) iss >> dummy;
            iss >> txBytes;

            interfaces.push_back({name, rxBytes + txBytes});
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


void processCommand(const std::string &received) {
    try {
        json j_in = json::parse(received);
        std::string cmd = j_in.value("cmd", "");
        json j_out;

        if (cmd == "PING") {
            j_out["type"] = "PONG";
            send_json(j_out);
        } else if (cmd == "KILL") {
            int pid = j_in.value("pid", -1);
            bool success = (pid > 0) && killProcess(pid);
            j_out["type"] = "KILL_RESULT";
            j_out["success"] = success;
            send_json(j_out);
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
            send_json(j_out);
        } else if (cmd == "KILL_GROUP") {
            int pgid = j_in.value("pgid", -1);
            bool success = (pgid > 0) ? killProcessGroup(pgid) : false;
            j_out["type"] = "KILL_RESULT";
            j_out["success"] = success;
            send_json(j_out);
        } else if (cmd == "STOP_SELF" || cmd == "BUSY") {
            keep_running = 0;
        } else if (cmd == "LIST_PROCESS") {
            auto procs = collectProcs();
            json procs_j = json::array();
            for (const auto &p : procs) procs_j.push_back(procToJson(p));
            j_out["type"] = "PROCESS_LIST";
            j_out["processes"] = procs_j;
            send_json(j_out);
        } else if (cmd == "CPU_PING") {
            j_out["type"] = "CPU_USAGE";
            j_out["usage"] = calculateCpuUsage();
            send_json(j_out);
        } else if (cmd == "SWAP_PING") {
            long used, total;
            getSwapUsage(used, total);
            j_out["type"] = "SWAP_USAGE";
            j_out["used"] = used;
            j_out["total"] = total;
            send_json(j_out);
        } else if (cmd == "GPU_PING") {
            j_out["type"] = "GPU_USAGE";
            j_out["usage"] = calculateGpuUsage();
            send_json(j_out);
        } else if (cmd == "CTEMP_PING") {
            j_out["type"] = "CPU_TEMP";
            j_out["temp"] = getCpuTemperatureCelsius();
            send_json(j_out);
        } else if (cmd == "PING_PID_CPU") {
            int pid = j_in.value("pid", -1);
            j_out["type"] = "PROCESS_CPU_USAGE";
            j_out["usage"] = calculateProcessCpuUsage(pid);
            send_json(j_out);
        } else if(cmd == "BAT_CHARGE_CYCLES"){
            j_out["type"] = "CHARGE_CYCLES";
            j_out["cycles"] = getBatteryCycleCount().value_or(-1);
            send_json(j_out);
        } else if (cmd == "LIST_NET_INTERFACES") {
            auto interfaces = listNetInterfaces();
            json interfaces_j = json::array();
            for (const auto& iface : interfaces) {
                interfaces_j.push_back({{"name", iface.name}, {"totalBytes", iface.totalBytes}});
            }
            j_out["type"] = "NET_INTERFACE_LIST";
            j_out["interfaces"] = interfaces_j;
            send_json(j_out);
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
                j_out["rxBytesPerSec"] = 0;
                j_out["txBytesPerSec"] = 0;
            }

            netStatCache[iface] = {curr.rxBytes, curr.txBytes, now};

            j_out["rxBytes"] = curr.rxBytes;
            j_out["txBytes"] = curr.txBytes;
            send_json(j_out);
        } else {
            log_line("Unknown command: " + cmd);
        }
    } catch (const std::exception& e) {
        log_line("JSON parse error: " + std::string(e.what()) + " | Data: " + received);
    }
}

int main() {
    signal(SIGINT, handle_sigint);
    signal(SIGTERM, handle_sigint);
    signal(SIGPIPE, SIG_IGN);

    const size_t BUF_SIZE = 8192;
    std::unique_ptr<char[]> buf(new char[BUF_SIZE]);
    std::string recv_buffer;

    while (keep_running) {
        ssize_t r = read(STDIN_FILENO, buf.get(), BUF_SIZE - 1);
        if (r > 0) {
            buf[r] = '\0';
            recv_buffer.append(buf.get(), r);
            size_t pos;
            while ((pos = recv_buffer.find('\n')) != std::string::npos) {
                std::string message = recv_buffer.substr(0, pos);
                recv_buffer.erase(0, pos + 1);
                if (!message.empty()) processCommand(message);
            }
        } else if (r == 0) {
            break;
        } else {
            if (errno == EINTR) continue;
            break;
        }
    }

    return 0;
}
