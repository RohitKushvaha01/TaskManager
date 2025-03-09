package com.rk.taskmanager.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import com.rk.taskmanager.TaskManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import java.io.Serializable
import java.nio.file.Files

interface IInterfaceCreator<T : IInterface> {
    fun asInterface(binder: IBinder): T
}

interface TaskManagerService : IInterface {
    fun listPs(): List<Proc>

    companion object {
        const val DESCRIPTOR = "com.rk.taskmanager.TaskManagerService"
        const val TRANSACTION_listPs = IBinder.FIRST_CALL_TRANSACTION

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
            return reply.createTypedArrayList(Proc.CREATOR) ?: emptyList()
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
            TaskManagerService.TRANSACTION_listPs -> {
                data.enforceInterface(TaskManagerService.DESCRIPTOR)

                val processDirs = (File("/proc").listFiles { file -> file.isDirectory && file.name.matches(Regex("\\d+")) } ?: emptyArray<File>())
                    .filter { it.name.toIntOrNull() != null }

                val uptimeSeconds = File("/proc/uptime").readText().split(" ")[0].toFloat() // System uptime in seconds

                val procs = processDirs.mapNotNull { dir ->
                    val pid = dir.name.toInt()

                    // Read cmdline and strip null bytes
                    val cmdlineFile = File(dir, "cmdline").readBytes()
                        .takeWhile { it != 0.toByte() }
                        .toByteArray()
                        .toString(Charsets.UTF_8)

                    val name = File(dir, "comm").readBytes()
                        .takeWhile { it != 0.toByte() }
                        .toByteArray()
                        .toString(Charsets.UTF_8)

                    // Read status file
                    val statusFile = File(dir, "status").readLines()
                    val uid = statusFile.firstOrNull { it.startsWith("Uid:") }
                        ?.split(Regex("\\s+"))?.get(1)?.toIntOrNull() ?: 0

                    val parentPid = statusFile.firstOrNull { it.startsWith("PPid:") }
                        ?.split(Regex("\\s+"))?.get(1)?.toIntOrNull() ?: 0

                    val memoryUsageKb = statusFile.firstOrNull { it.startsWith("VmRSS:") }
                        ?.split(Regex("\\s+"))?.get(1)?.toLongOrNull() ?: 0

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

                    // Calculate CPU usage percentage
                    val totalTime = utime + stime
                    val seconds = uptimeSeconds - (startTime / 100f)
                    val cpuUsage = if (seconds > 0) ((totalTime.toFloat() / 100f) / seconds) * 100 else 0f

                    // Return the fully filled Proc object
                    Proc(
                        name = name,
                        pid = pid,
                        uid = uid,
                        cpuUsage = cpuUsage,
                        parentPid = parentPid,
                        isForeground = isForeground,
                        memoryUsageKb = memoryUsageKb,
                        cmdLine = cmdlineFile
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
