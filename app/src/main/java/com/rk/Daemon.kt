package com.rk

import android.content.Context
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.screens.WorkingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.net.LocalServerSocket
import android.net.LocalSocket
import androidx.compose.runtime.*
import com.rk.DaemonServer.received_messages
import com.rk.taskmanager.shizuku.ShizukuShell
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

val daemon_messages = received_messages.asSharedFlow()
val send_daemon_messages = MutableSharedFlow<String>(extraBufferCapacity = 10,onBufferOverflow = BufferOverflow.DROP_OLDEST)
var isConnected by mutableStateOf(false)
    private set

private object DaemonServer {

    private var server: LocalServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val running = AtomicBoolean(false)

    val received_messages = MutableSharedFlow<String>(extraBufferCapacity = 10,onBufferOverflow = BufferOverflow.DROP_OLDEST)


    private var acceptJob: Job? = null
    private var clientJob: Job? = null
    private var currentClient: LocalSocket? = null

    // --- Logging helper ---
    private fun log(msg: String) {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        println("[$ts] [DaemonServer] $msg")
    }

    suspend fun start(): Exception? {
        if (running.get()) {
            log("Server already running, ignoring start request")
            return null
        }

        return try {
            server = LocalServerSocket("TaskmanagerD")
            running.set(true)
            log("Server started on socket: TaskmanagerD")
            startAccepting()
            null
        } catch (e: IOException) {
            log("ERROR: Failed to start server: ${e.message}")
            e.printStackTrace()
            server = null
            e
        }
    }

    private fun startAccepting() {
        acceptJob = scope.launch {
            val srv = server ?: return@launch
            log("Accept loop started, waiting for client...")
            while (isActive && running.get()) {
                try {
                    val client = srv.accept()
                    log("Incoming client connection")

                    if (currentClient != null) {
                        log("Client rejected (already connected)")
                        try {
                            client.outputStream.write("BUSY\n".toByteArray())
                            client.outputStream.flush()
                        } catch (_: IOException) {}
                        client.close()
                        continue
                    }

                    log("Client accepted")
                    currentClient = client
                    handleClient(client)

                } catch (e: IOException) {
                    if (running.get()) {
                        log("ERROR in accept loop: ${e.message}")
                        e.printStackTrace()
                    }
                    break
                }
            }
            log("Accept loop terminated")
        }
    }

    private fun handleClient(client: LocalSocket) {
        clientJob = scope.launch {
            isConnected = true
            log("Client handler started")
            val input = client.inputStream
            val buf = ByteArray(1024)

            try {
                scope.launch(Dispatchers.IO) {
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

                send_daemon_messages.asSharedFlow().collect {
                    client.outputStream.write("$it\n".toByteArray())
                    client.outputStream.flush()
                }

            } catch (e: IOException) {
                log("ERROR while handling client: ${e.message}")
                e.printStackTrace()
            } finally {
                log("Client disconnected")
                isConnected = false
                cleanupClient()
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
        running.set(false)
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

enum class DaemonResult(var message: String?){
    OK(null),
    SHIZUKU_PERMISSION_DENIED("Shizuku permission denied, grant permission manually"),
    SHIZUKU_NOT_RUNNING("Shizuku not running or not installed."),
    SU_NOT_IN_PATH("Su not available in path"),
    UNKNOWN_ERROR(null),
    DAEMON_REFUSED("Daemon failed to start"),
    DAEMON_ALREADY_BEING_STARTED(null)
}


private var daemonCalled = false
suspend fun startDaemon(
    context: Context,
    mode: Int
): DaemonResult {
    val daemonFile = File(TaskManager.getContext().applicationInfo.nativeLibraryDir,"libtaskmanagerd.so")
    val result = withContext(Dispatchers.IO) {
        if (daemonCalled){
            return@withContext DaemonResult.DAEMON_ALREADY_BEING_STARTED
        }
        daemonCalled = true

        println(daemonFile.absolutePath)

        DaemonServer.start()

        try {
            when (mode) {
                WorkingMode.SHIZUKU.id -> {
                    val loading = LoadingPopup(ctx = context)
                    loading.setMessage("Starting daemon...")
                    loading.show()

                    if (!ShizukuShell.isShizukuRunning()){
                        loading.hide()
                        return@withContext DaemonResult.SHIZUKU_NOT_RUNNING
                    }

                    if (!ShizukuShell.isPermissionGranted()) {
                        loading.hide()
                        return@withContext DaemonResult.SHIZUKU_PERMISSION_DENIED
                    }

                    val processResult = ShizukuShell.newProcess(
                        cmd = arrayOf(daemonFile.absolutePath),
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
                    loading.setMessage("Starting daemon...")
                    loading.show()

                    if (!isSuInPath()){
                        loading.hide()
                        return@withContext DaemonResult.SU_NOT_IN_PATH
                    }
                    val cmd = arrayOf("su", "-c", daemonFile.absolutePath)
                    val result = newProcess(cmd = cmd, env = arrayOf(), workingDir = "/")
                    if (result.first == 0){
                        loading.hide()
                        DaemonResult.OK
                    }else{
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
): Pair<Int,String> = withContext(Dispatchers.IO){
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
        Pair(process.waitFor(),process.inputStream.bufferedReader().readLine())
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(-1,e.message.toString())
    }
}
