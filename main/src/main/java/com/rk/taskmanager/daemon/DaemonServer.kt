package com.rk.taskmanager.daemon

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val daemon_messages = DaemonServer.received_messages.asSharedFlow()
val send_daemon_messages = MutableSharedFlow<String>(extraBufferCapacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)
var isConnected by mutableStateOf(false)
    private set

object DaemonServer {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val received_messages =
        MutableSharedFlow<String>(extraBufferCapacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var readerJob: Job? = null
    private var writerJob: Job? = null

    private fun log(msg: String) {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        Log.d("DaemonServer", "[$ts] $msg")
        println("[$ts] [DaemonServer] $msg")
    }

    suspend fun start(input: InputStream, output: OutputStream): Boolean {
        if (readerJob?.isActive == true) {
            log("Daemon already running, ignoring start request")
            return true
        }

        return try {
            startHandling(input, output)
            true
        } catch (e: IOException) {
            log("ERROR: Failed to start daemon I/O: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun startHandling(input: InputStream, output: OutputStream) {
        readerJob = scope.launch {
            isConnected = true
            log("Daemon I/O started")
            try {
                val reader = input.bufferedReader()
                while (isActive) {
                    val message = reader.readLine() ?: break
                    if (message.isNotEmpty()) {
                        Log.d("DaemonServer", "RECV: $message")
                        received_messages.emit(message.trim())
                    }
                }
            } catch (e: IOException) {
                log("Reader error: ${e.message}")
                e.printStackTrace()
            } finally {
                isConnected = false
                log("Reader terminated")
            }
        }

        writerJob = scope.launch {
            try {
                send_daemon_messages.collect { message ->
                    Log.d("DaemonServer", "SEND: $message")
                    withContext(Dispatchers.IO) {
                        output.write("$message\n".toByteArray())
                        output.flush()
                    }
                }
            } catch (e: IOException) {
                log("Writer error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    suspend fun stop() {
        log("Stopping daemon I/O...")
        isConnected = false
        readerJob?.cancelAndJoin()
        readerJob = null
        writerJob?.cancelAndJoin()
        writerJob = null
        log("Daemon I/O stopped")
    }
}
