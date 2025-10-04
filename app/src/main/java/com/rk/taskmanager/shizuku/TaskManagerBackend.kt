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
            TaskManagerService.TRANSACTION_newProcess -> {
                data.enforceInterface(TaskManagerService.DESCRIPTOR)

                // Read parameters from parcel
                val cmd = data.createStringArray() ?: emptyArray()
                val env = data.createStringArray() ?: emptyArray()
                val workingDir = data.readString() ?: ""

                // Execute the process
                val result = newProcess(cmd, env, workingDir)

                // Write response
                reply?.writeNoException()
                reply?.writeInt(result)
                return true
            }

            else -> return super.onTransact(code, data, reply, flags)
        }
    }

    fun newProcess(
        cmd: Array<String>,
        env: Array<String>,
        workingDir: String
    ): Int {
        return try {
            val processBuilder = ProcessBuilder(*cmd)

            // Set working directory if provided
            if (workingDir.isNotEmpty()) {
                val dir = File(workingDir)
                if (dir.exists() && dir.isDirectory) {
                    processBuilder.directory(dir)
                }
            }

            // Set environment variables if provided
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

            // Start the process
            processBuilder.start().waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
            // Return 1 for failure
            1
        }
    }
}