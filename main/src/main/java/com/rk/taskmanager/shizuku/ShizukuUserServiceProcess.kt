package com.rk.taskmanager.shizuku

import android.content.ServiceConnection
import android.os.ParcelFileDescriptor
import com.rk.taskmanager.daemon.IDaemonUserService
import rikka.shizuku.Shizuku
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * [Process] backed by a Shizuku UserService.
 *
 * The user service (running as shell/root) starts the native daemon and exposes its
 * stdio as pipes, so this class only has to adapt those pipes to [Process].
 *
 * [destroy] asks the user service to stop the daemon and then unbinds it.
 */
internal class ShizukuUserServiceProcess(
    private val service: IDaemonUserService,
    private val args: Shizuku.UserServiceArgs,
    private val connection: ServiceConnection,
    stdin: ParcelFileDescriptor,
    stdout: ParcelFileDescriptor
) : Process() {

    private val stdinStream = ParcelFileDescriptor.AutoCloseOutputStream(stdin)
    private val stdoutStream = ParcelFileDescriptor.AutoCloseInputStream(stdout)
    private val emptyStream = ByteArrayInputStream(ByteArray(0))

    private var destroyed = false

    override fun getOutputStream(): OutputStream = stdinStream

    override fun getInputStream(): InputStream = stdoutStream

    override fun getErrorStream(): InputStream = emptyStream

    override fun waitFor(): Int =
        throw UnsupportedOperationException("Shizuku user service process is long-running")

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = false

    override fun exitValue(): Int =
        if (destroyed) 0 else throw IllegalThreadStateException("process is still running")

    override fun isAlive(): Boolean = !destroyed

    override fun destroy() {
        if (destroyed) return
        destroyed = true

        // One-way transaction handled by Shizuku's reserved destroy code.
        runCatching { service.destroy() }
        // Drop the local connection first, then ask Shizuku to remove the service.
        runCatching { Shizuku.unbindUserService(args, connection, false) }
        runCatching { Shizuku.unbindUserService(args, connection, true) }

        runCatching { stdinStream.close() }
        runCatching { stdoutStream.close() }
    }
}
