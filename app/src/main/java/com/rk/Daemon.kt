package com.rk

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastJoinToString
import com.rk.DaemonServer.received_messages
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.getString
import com.rk.taskmanager.screens.WorkingMode
import com.rk.taskmanager.shizuku.ShizukuShell
import com.rk.taskmanager.strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val daemon_messages = received_messages.asSharedFlow()
val send_daemon_messages = MutableSharedFlow<String>(extraBufferCapacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)
var isConnected by mutableStateOf(false)
    private set

private object DaemonServer {

    private var server: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val received_messages =
        MutableSharedFlow<String>(extraBufferCapacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)


    private var acceptJob: Job? = null
    private var clientJob: Job? = null
    private var currentClient: Socket? = null

    // --- Logging helper ---
    private fun log(msg: String) {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        println("[$ts] [DaemonServer] $msg")
    }

    suspend fun start(): Pair<Int, Exception?> {
        if (server != null && server!!.isBound) {
            log("Server already running, ignoring start request")
            return Pair(server!!.localPort, null)
        }

        return try {
            server = ServerSocket(0, 1, InetAddress.getByName("0.0.0.0"))
            log("Server started on port: ${server!!.localPort}")
            startAccepting()
            Pair(server!!.localPort, null)
        } catch (e: IOException) {
            log("ERROR: Failed to start server: ${e.message}")
            e.printStackTrace()
            server = null
            Pair(-1, e)
        }
    }

    private fun startAccepting() {
        acceptJob = scope.launch {
            val srv = server ?: return@launch
            log("Accept loop started, waiting for client...")
            while (isActive && server != null && server!!.isBound) {
                try {
                    val client = srv.accept()
                    log("Incoming client connection")

                    if (currentClient != null) {
                        log("Client rejected (already connected)")
                        try {
                            client.outputStream.write("BUSY\n".toByteArray())
                            client.outputStream.flush()
                        } catch (_: IOException) {
                        }
                        client.close()
                        continue
                    }

                    log("Client accepted")
                    currentClient = client
                    handleClient(client)

                } catch (e: IOException) {
                    if (server != null && server!!.isBound) {
                        log("ERROR in accept loop: ${e.message}")
                        e.printStackTrace()
                    }
                    break
                }
            }
            log("Accept loop terminated")
        }
    }

    private fun handleClient(client: Socket) {
        clientJob = scope.launch {
            isConnected = true
            log("Client handler started")
            val input = client.inputStream

            try {
                val readerJob = launch(Dispatchers.IO) {
                    runCatching {
                        val reader = input.bufferedReader()
                        while (isActive) {
                            val message = reader.readLine()
                            if (message == null) break

                            if (message.isNotEmpty()) {
                                received_messages.emit(message.trim())
                            }
                        }
                    }.onFailure { it.printStackTrace() }
                }

                val writerJob = launch(Dispatchers.IO) {
                    runCatching {
                        send_daemon_messages.asSharedFlow().collect {
                            client.outputStream.write("$it\n".toByteArray())
                            client.outputStream.flush()
                        }
                    }.onFailure {
                        log("Writer error: ${it.message}")
                        it.printStackTrace()
                    }
                }

                readerJob.join()
                writerJob.cancelAndJoin()
                cleanupClient()

            } catch (e: IOException) {
                log("ERROR while handling client: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun cleanupClient() {
        log("Cleaning up client resources")
        try {
            currentClient?.close()
        } catch (_: IOException) {
            log("WARNING: Failed to close client socket")
        }
        currentClient = null
        clientJob?.cancelAndJoin()
        clientJob = null
    }

    suspend fun stop() {
        log("Stopping server...")
        isConnected = false
        acceptJob?.cancelAndJoin()
        acceptJob = null
        cleanupClient()
        try {
            server?.close()
            log("Server socket closed")
        } catch (_: IOException) {
            log("WARNING: Failed to close server socket")
        }
        server = null
        log("Server stopped")
    }
}

enum class DaemonResult(var message: String?) {
    OK(null),
    SHIZUKU_PERMISSION_DENIED(strings.shizuku_permission_denied.getString()),
    SHIZUKU_NOT_RUNNING(if (ShizukuShell.isShizukuInstalled()) strings.shizuku_not_running.getString() else strings.shizuku_not_installed.getString()),
    SU_NOT_IN_PATH(strings.su_not_in_path.getString()),
    UNKNOWN_ERROR(null),
    DAEMON_REFUSED(strings.daemon_not_started.getString()),
    DAEMON_ALREADY_BEING_STARTED(null)
}


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

        println(daemonFile.absolutePath)

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
                    val loading = LoadingPopup(ctx = context)
                    loading.setMessage(strings.starting_daemon.getString())
                    loading.show()

                    if (!ShizukuShell.isShizukuRunning()) {
                        loading.hide()
                        return@withContext DaemonResult.SHIZUKU_NOT_RUNNING
                    }

                    if (!ShizukuShell.isPermissionGranted()) {
                        loading.hide()
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

                    loading.hide()
                    result

                }

                WorkingMode.ROOT.id -> {
                    val loading = LoadingPopup(ctx = context)
                    loading.setMessage(strings.starting_daemon.getString())
                    loading.show()

                    if (!isSuInPath()) {
                        loading.hide()
                        return@withContext DaemonResult.SU_NOT_IN_PATH
                    }
                    val cmd = arrayOf("su", "-c", daemonFile.absolutePath, "-p", port.toString(), "-D")
                    val result = newProcess(cmd = cmd, env = arrayOf(), workingDir = "/")
                    if (result.first == 0) {
                        loading.hide()
                        DaemonResult.OK
                    } else {
                        loading.hide()
                        DaemonResult.DAEMON_REFUSED.also {
                            it.message = result.second
                        }
                    }
                }

                else -> {
                    throw IllegalStateException("This should not happen")
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

suspend fun isSuInPath(): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
        val result = process.inputStream.bufferedReader().readLine()
        result != null
    } catch (e: Exception) {
        false
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
