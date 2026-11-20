package com.rk.taskmanager.daemon

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.Keep
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Runs inside the user-service process that Shizuku starts as shell/root.
 *
 * It starts the native taskmanagerd binary and relays its stdin/stdout over pipes
 * provided by the app, because [java.lang.Process] does not expose the child's file
 * descriptors to Java. The app owns the other ends of the pipes, so an app death
 * shows up as EOF and the daemon is not orphaned.
 *
 * Shizuku instantiates this class by name: it first tries the constructor that takes
 * a [Context] (Shizuku API 13+) and falls back to the no-arg constructor.
 */
@Keep
class DaemonUserService : IDaemonUserService.Stub {

    private var child: Process? = null

    private var appToChildIn: InputStream? = null
    private var childStdinOut: OutputStream? = null
    private var childStdoutIn: InputStream? = null
    private var childToAppOut: OutputStream? = null

    private val running = AtomicBoolean(false)

    constructor() : super()

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : super()

    @Synchronized
    override fun start(
        daemonPath: String,
        workingDir: String,
        stdinRead: ParcelFileDescriptor,
        stdoutWrite: ParcelFileDescriptor
    ) {
        stopChild()

        val process = try {
            val builder = ProcessBuilder(daemonPath).directory(File(workingDir))
            // Match the legacy Runtime.exec(cmd, emptyEnv, "/") behaviour.
            builder.environment().clear()
            builder.start()
        } catch (t: Throwable) {
            runCatching { stdinRead.close() }
            runCatching { stdoutWrite.close() }
            throw t
        }
        child = process

        val appToChild = ParcelFileDescriptor.AutoCloseInputStream(stdinRead)
        val childStdin = process.outputStream
        val childStdout = process.inputStream
        val childToApp = ParcelFileDescriptor.AutoCloseOutputStream(stdoutWrite)

        appToChildIn = appToChild
        childStdinOut = childStdin
        childStdoutIn = childStdout
        childToAppOut = childToApp

        running.set(true)
        Thread({ relay(appToChild, childStdin, flushEachWrite = true) }, "taskmanagerd-stdin")
            .apply { isDaemon = true }
            .start()
        Thread({ relay(childStdout, childToApp, flushEachWrite = false) }, "taskmanagerd-stdout")
            .apply { isDaemon = true }
            .start()

        Log.i(TAG, "started $daemonPath")
    }

    private fun relay(input: InputStream, output: OutputStream, flushEachWrite: Boolean) {
        val buffer = ByteArray(8 * 1024)
        try {
            while (running.get()) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                if (flushEachWrite) output.flush()
            }
        } catch (t: Throwable) {
            if (running.get()) {
                Log.w(TAG, "relay stopped: ${t.message}")
            }
        } finally {
            runCatching { input.close() }
            runCatching { output.close() }
        }
    }

    @Synchronized
    override fun destroy() {
        stopChild()
        exitProcess(0)
    }

    private fun stopChild() {
        running.set(false)
        runCatching { child?.destroy() }
        child = null
        runCatching { appToChildIn?.close() }
        runCatching { childStdinOut?.close() }
        runCatching { childStdoutIn?.close() }
        runCatching { childToAppOut?.close() }
        appToChildIn = null
        childStdinOut = null
        childStdoutIn = null
        childToAppOut = null
    }

    companion object {
        private const val TAG = "DaemonUserService"
    }
}
