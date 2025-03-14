package com.rk.taskmanager.shizuku

import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import androidx.annotation.RequiresApi
import com.rk.taskmanager.TaskManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import kotlin.random.Random
import kotlin.random.asJavaRandom

interface IInterfaceCreator<T : IInterface> {
    fun asInterface(binder: IBinder): T
}

interface TaskManagerService : IInterface {
    fun listPs(): List<Proc>
    fun getCpuUsage(): Byte
    fun getRamUsageMB(): Int

    companion object {
        const val DESCRIPTOR = "com.rk.taskmanager.TaskManagerService"
        const val TRANSACTION_listPs = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_getCpuUsage = IBinder.FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_getRamUsage = IBinder.FIRST_CALL_TRANSACTION + 2

        val CREATOR = object : IInterfaceCreator<TaskManagerService> {
            override fun asInterface(binder: IBinder): TaskManagerService {
                return ServiceStub(binder)
            }
        }
    }
}


// Client-side implementation
private class ServiceStub(private val binder: IBinder) : TaskManagerService {
    override fun listPs(): List<Proc> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        try {
            data.writeInterfaceToken(TaskManagerService.DESCRIPTOR)
            binder.transact(TaskManagerService.TRANSACTION_listPs, data, reply, 0)
            reply.readException()
            return mutableListOf<Proc>().apply {
                reply.readTypedList(this, Proc.CREATOR)
            }
        } finally {
            data.recycle()
            reply.recycle()
        }
    }


    override fun getCpuUsage(): Byte {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(TaskManagerService.DESCRIPTOR)
            binder.transact(TaskManagerService.TRANSACTION_getCpuUsage, data, reply, 0)
            reply.readException()
            reply.readByte()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    override fun getRamUsageMB(): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(TaskManagerService.DESCRIPTOR)
            binder.transact(TaskManagerService.TRANSACTION_getRamUsage, data, reply, 0)
            reply.readException()
            reply.readInt()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    override fun asBinder(): IBinder {
        return binder
    }
}


// Server-side implementation
class TaskManagerServiceImpl : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            TaskManagerService.TRANSACTION_getRamUsage -> {
                data.enforceInterface(TaskManagerService.DESCRIPTOR)

                //TODO
                reply?.writeNoException()
                reply?.writeInt(0)
                return true
            }


            TaskManagerService.TRANSACTION_getCpuUsage -> {
                data.enforceInterface(TaskManagerService.DESCRIPTOR)

                fun calculateCpuUsage(): Int {
                    data class CpuStat(val user: Long, val nice: Long, val system: Long, val idle: Long,
                                       val iowait: Long, val irq: Long, val softirq: Long, val steal: Long) {
                        fun total() = user + nice + system + idle + iowait + irq + softirq + steal
                        fun active() = total() - idle
                    }

                    fun readCpuStat(): CpuStat? {
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
                    val cmdlineFile = if (File(dir, "cmdline").exists()){
                        File(dir, "cmdline").readBytes()
                            .takeWhile { it != 0.toByte() }
                            .toByteArray()
                            .toString(Charsets.UTF_8)
                    }else{
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

                    // Read status file
                    val statusFile = File(dir, "status").readLines()
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
                    val statFile = File(dir, "stat").readText().split(" ")
                    val utime = statFile.getOrNull(13)?.toLongOrNull() ?: 0
                    val stime = statFile.getOrNull(14)?.toLongOrNull() ?: 0
                    val startTime = statFile.getOrNull(21)?.toLongOrNull() ?: 0
                    val niceness = statFile.getOrNull(17)?.toIntOrNull() ?: 0

                    // Calculate CPU usage percentage
                    val totalTime = utime + stime
                    val seconds = uptimeSeconds - (startTime / 100f)
                    val cpuUsage = if (seconds > 0) ((totalTime.toFloat() / 100f) / seconds) * 100 else 0f

                    val clockTicksPerSecond = 100  // Usually 100 on Linux
                    val elapsedTime = uptimeSeconds - (startTime / clockTicksPerSecond)

                    fun getPageSizeKb(): Int {
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
