package com.rk.taskmanager.shizuku

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Binder
import android.os.Parcel
import android.system.Os
import android.system.OsConstants
import androidx.annotation.Keep
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

@Keep class TaskManagerBackend : Binder() {

    @Keep
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            TaskManagerService.TRANSACTION_killPid -> {
                data.enforceInterface(TaskManagerService.DESCRIPTOR)

                val pid = data.readInt()
                val signal = data.readInt()

                val result = try {
                    Os.kill(pid, signal)
                    true
                } catch (_: Exception) {
                    false
                }

                reply?.writeNoException()
                reply?.writeInt(if (result) 1 else 0)
                return true
            }


            TaskManagerService.TRANSACTION_getCpuUsage -> {
                data.enforceInterface(TaskManagerService.DESCRIPTOR)

                @Keep fun calculateCpuUsage(): Int {
                    data @Keep class CpuStat(val user: Long, val nice: Long, val system: Long, val idle: Long,
                                             val iowait: Long, val irq: Long, val softirq: Long, val steal: Long) {
                        @Keep fun total() = user + nice + system + idle + iowait + irq + softirq + steal
                        @Keep fun active() = total() - idle
                    }

                    @Keep fun readCpuStat(): CpuStat? {
                        val line = File("/proc/stat").useLines { it.firstOrNull { line -> line.startsWith("cpu ") } }
                        return line?.split("\\s+".toRegex())?.drop(1)?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size >= 8 }?.let {
                            CpuStat(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7])
                        }
                    }

                    val prev = readCpuStat() ?: return 0
                    Thread.sleep(100)
                    val curr = readCpuStat() ?: return 0

                    val totalDiff = (curr.total() - prev.total()).toDouble()
                    val activeDiff = (curr.active() - prev.active()).toDouble()

                    return if (totalDiff > 0) ((activeDiff / totalDiff) * 100).toInt() else 0
                }


                val cpuUsage = calculateCpuUsage().toByte()
                reply?.writeNoException()
                reply?.writeByte(cpuUsage)
                return true
            }

            TaskManagerService.TRANSACTION_listPs -> {
                data.enforceInterface(TaskManagerService.DESCRIPTOR)

                val processDirs = (File("/proc").listFiles { file -> file.isDirectory && file.name.matches(Regex("\\d+")) } ?: emptyArray<File>())
                    .filter { it.name.toIntOrNull() != null }

                val uptimeSeconds = File("/proc/uptime").readText().split(" ")[0].toFloat() // System uptime in seconds

                val procs = processDirs.mapNotNull { dir ->
                    val pid = dir.name.toInt()

                    // Read cmdline and strip null bytes
                    val cmdlineFile = if (File(dir, "cmdline").exists()) {
                        File(dir, "cmdline").readBytes()
                            .toString(Charsets.UTF_8)
                            .split('\u0000')
                            .firstOrNull() ?: ""
                    } else {
                        ""
                    }


                    val name = if (File(dir, "comm").exists()){
                        File(dir, "comm").readBytes()
                            .takeWhile { it != 0.toByte() }
                            .toByteArray()
                            .toString(Charsets.UTF_8)
                    }else{
                        ""
                    }


                    val statusFile = if (File(dir, "status").exists()){File(dir, "status").readLines()}else{emptyList()}

                    val uid = statusFile.firstOrNull { it.startsWith("Uid:") }
                        ?.split(Regex("\\s+"))?.get(1)?.toIntOrNull() ?: 0

                    val parentPid = statusFile.firstOrNull { it.startsWith("PPid:") }
                        ?.split(Regex("\\s+"))?.get(1)?.toIntOrNull() ?: 0

                    val memoryUsageKb = statusFile.firstOrNull { it.startsWith("VmRSS:") }
                        ?.split(Regex("\\s+"))?.get(1)?.toLongOrNull() ?: 0

                    // Get process state
                    val state = statusFile.firstOrNull { it.startsWith("State:") }
                        ?.split(Regex("\\s+"))?.get(1) ?: "?"

                    // Get number of threads
                    val threads = statusFile.firstOrNull { it.startsWith("Threads:") }
                        ?.split(Regex("\\s+"))?.get(1)?.toIntOrNull() ?: 1

                    // Check if the process is foreground
                    val oomScoreAdjFile = File(dir, "oom_score_adj")
                    val isForeground =
                        oomScoreAdjFile.exists() && (oomScoreAdjFile.readText().trim().toIntOrNull()
                            ?: 1000) <= 0

                    // Read the stat file for CPU usage
                    val statFile = if (File(dir, "stat").exists()){File(dir, "stat").readText().split(" ")}else{emptyList()}
                    val utime = statFile.getOrNull(13)?.toLongOrNull() ?: 0
                    val stime = statFile.getOrNull(14)?.toLongOrNull() ?: 0
                    val startTime = statFile.getOrNull(21)?.toLongOrNull() ?: 0
                    val niceness = statFile.getOrNull(17)?.toIntOrNull() ?: 0

                    // Calculate CPU usage percentage
                    val totalTime = utime + stime
                    val seconds = uptimeSeconds - (startTime / 100f)
                    val numCores = Runtime.getRuntime().availableProcessors()
                    val cpuUsageRaw = if (seconds > 0) ((totalTime.toFloat() / 100f) / seconds) * 100 else 0f
                    val cpuUsage = (cpuUsageRaw / numCores).coerceIn(0f, 100f)

                    val clockTicksPerSecond = Os.sysconf(OsConstants._SC_CLK_TCK)
                    val elapsedTime = uptimeSeconds - (startTime / clockTicksPerSecond)

                    @Keep fun getPageSizeKb(): Int {
                        val statusFile = "/proc/self/status"
                        val pageSizeLine = "VmPageSize:"

                        val pageSize = File(statusFile).useLines { lines ->
                            lines.firstOrNull { it.startsWith(pageSizeLine) }
                                ?.split("\\s+".toRegex())?.get(1)?.toIntOrNull()
                        }

                        return pageSize ?: 4
                    }

                    val pageSizeKb = getPageSizeKb()
                    val residentSetSizeKb = (statFile.getOrNull(23)?.toLongOrNull() ?: 0) * pageSizeKb

                    val cgroupFile = File(dir, "cgroup")
                    val cgroup = if (cgroupFile.exists()) cgroupFile.readLines().joinToString("; ") else "unknown"

                    val exeFile = File(dir, "exe")
                    val executablePath = if (exeFile.exists()) exeFile.canonicalPath else "null"

                    val virtualMemoryKb = File(dir, "status").readLines()
                        .firstOrNull { it.startsWith("VmSize:") }
                        ?.split(Regex("\\s+"))?.get(1)?.toLongOrNull()  // Extract value in KB



                    // Return the fully filled Proc object
                    Proc(
                        name = name,
                        nice = niceness,
                        pid = pid,
                        uid = uid,
                        cpuUsage = cpuUsage,
                        parentPid = parentPid,
                        isForeground = isForeground,
                        memoryUsageKb = memoryUsageKb,
                        cmdLine = cmdlineFile,
                        state = state,
                        threads = threads,
                        startTime = startTime,
                        elapsedTime = elapsedTime,
                        residentSetSizeKb = residentSetSizeKb,
                        virtualMemoryKb = virtualMemoryKb ?: 0,
                        cgroup = cgroup,
                        executablePath = executablePath,
                    )
                }



                reply?.writeNoException()
                reply?.writeTypedList(procs)
                return true
            }
            else -> return super.onTransact(code, data, reply, flags)
        }
    }
}