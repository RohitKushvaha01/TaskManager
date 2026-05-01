package com.rk.taskmanager.daemon

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val daemon_messages = DaemonServer.received_messages.asSharedFlow()
val send_daemon_messages = MutableSharedFlow<String>(extraBufferCapacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)
var isConnected by mutableStateOf(false)
    private set

object DaemonServer {

    private var server: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val received_messages =
        MutableSharedFlow<String>(extraBufferCapacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var acceptJob: Job? = null
    private var clientJob: Job? = null
    private var currentClient: Socket? = null

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
                            val busy = JSONObject().apply { put("cmd", "BUSY") }
                            client.outputStream.write("${busy}\n".toByteArray())
                            client.outputStream.flush()
                        } catch (_: IOException) {}
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
        isConnected = false
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