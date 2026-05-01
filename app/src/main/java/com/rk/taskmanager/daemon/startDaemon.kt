package com.rk.taskmanager.daemon

import android.content.Context
import android.util.Log
import androidx.compose.ui.util.fastJoinToString
import com.rk.taskmanager.TaskManager
import com.rk.commons.getString
import com.rk.taskmanager.settings.WorkingMode
import com.rk.taskmanager.shizuku.ShizukuShell
import com.rk.commons.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


private var daemonCalled = false
suspend fun startDaemon(
    context: Context,
    mode: Int
): DaemonResult {
    val daemonFile = File(TaskManager.requireContext().applicationInfo.nativeLibraryDir, "libtaskmanagerd.so")
    val result = withContext(Dispatchers.IO) {
        if (daemonCalled) {
            return@withContext DaemonResult.DAEMON_ALREADY_BEING_STARTED
        }
        daemonCalled = true

        val daemonServer = DaemonServer.start()
        if (daemonServer.second != null) {
            return@withContext DaemonResult.UNKNOWN_ERROR.also { it.message = daemonServer.second?.message.toString() }
        }

        val port = daemonServer.first
        if (port <= 0) {
            return@withContext DaemonResult.UNKNOWN_ERROR.also {
                it.message = strings.port_busy.getString(mapOf("%port" to port.toString()))
            }
        }

        try {
            when (mode) {
                WorkingMode.SHIZUKU.id -> {
                    if (!ShizukuShell.isShizukuRunning()) {
                        return@withContext DaemonResult.SHIZUKU_NOT_RUNNING
                    }

                    if (!ShizukuShell.isPermissionGranted()) {
                        return@withContext DaemonResult.SHIZUKU_PERMISSION_DENIED
                    }

                    val processResult = ShizukuShell.newProcess(
                        cmd = arrayOf(daemonFile.absolutePath, "-p", port.toString(), "-D"),
                        env = arrayOf(),
                        dir = "/"
                    )

                    val result = if (processResult.first == 0) {
                        DaemonResult.OK
                    } else {
                        DaemonResult.DAEMON_REFUSED.also {
                            it.message = processResult.second
                        }
                    }



                    result

                }

                WorkingMode.ROOT.id -> {
                    val suCheck = isSuWorking()

                    if (!suCheck.first){
                        return@withContext DaemonResult.SU_FAILED.also { it.message = suCheck.second?.message ?: "unknown error" }
                    }

                    //val cmd = arrayOf("su", "-c", daemonFile.absolutePath, "-p", port.toString(), "-D")
                    val cmd = arrayOf("su", "-c", "${daemonFile.absolutePath} -p ${port.toString()} -D")
                    val result = newProcess(cmd = cmd, env = arrayOf(), workingDir = "/")
                    if (result.first == 0) {
                        DaemonResult.OK
                    } else {
                        DaemonResult.DAEMON_REFUSED.also {
                            it.message = result.second
                        }
                    }
                }


                WorkingMode.NOT_SET.id -> {
                    DaemonResult.SKIPPED
                }

                else -> {
                    Log.e("startDaemon", "Unknown working mode $mode")
                    DaemonResult.SKIPPED
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            DaemonResult.UNKNOWN_ERROR.also {
                it.message = e.message
            }
        }
    }

    daemonCalled = false
    return result
}

suspend fun isSuWorking(): Pair<Boolean, Exception?> = withContext(Dispatchers.IO) {
    try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id -u"))
        val output = process.inputStream.bufferedReader().readLine()
        process.waitFor()
        Pair(output == "0",null)
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(false,e)
    }
}


private suspend fun newProcess(
    cmd: Array<String>,
    env: Array<String>,
    workingDir: String
): Pair<Int, String> = withContext(Dispatchers.IO) {
    return@withContext try {
        val processBuilder = ProcessBuilder(*cmd)
        processBuilder.redirectErrorStream(true)
        if (workingDir.isNotEmpty()) {
            val dir = File(workingDir)
            if (dir.exists() && dir.isDirectory) {
                processBuilder.directory(dir)
            }
        }

        if (env.isNotEmpty()) {
            val environment = processBuilder.environment()
            environment.clear()
            env.forEach { envVar ->
                val parts = envVar.split("=", limit = 2)
                if (parts.size == 2) {
                    environment[parts[0]] = parts[1]
                }
            }
        }

        val process = processBuilder.start()
        Pair(process.waitFor(), process.inputStream.bufferedReader().readLines().fastJoinToString("\n"))
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(-1, e.message.toString())
    }
}
