package com.rk.taskmanager.shizuku

import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import moe.shizuku.server.IRemoteProcess
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * [Process] implementation backed by the [IRemoteProcess] binder returned by
 * `IShizukuService.newProcess`.
 *
 * This is the replacement for `rikka.shizuku.ShizukuRemoteProcess`, whose factory
 * (`Shizuku.newProcess`) is deprecated, private since Shizuku API 13 and scheduled
 * for removal in API 14. The remote side is still started through the same
 * `IShizukuService.newProcess` AIDL transaction, so behaviour is unchanged.
 *
 * The reference to [remoteProcess] must be kept for as long as the process is in
 * use: the binder is what keeps the process alive on the Shizuku server.
 */
internal class ShizukuProcess(
    private val remoteProcess: IRemoteProcess
) : Process() {

    private var stdin: OutputStream? = null
    private var stdout: InputStream? = null
    private var stderr: InputStream? = null

    override fun getOutputStream(): OutputStream =
        stdin ?: withRemote {
            ParcelFileDescriptor.AutoCloseOutputStream(remoteProcess.outputStream)
        }.also { stdin = it }

    override fun getInputStream(): InputStream =
        stdout ?: withRemote {
            ParcelFileDescriptor.AutoCloseInputStream(remoteProcess.inputStream)
        }.also { stdout = it }

    override fun getErrorStream(): InputStream =
        stderr ?: withRemote {
            ParcelFileDescriptor.AutoCloseInputStream(remoteProcess.errorStream)
        }.also { stderr = it }

    override fun waitFor(): Int = withRemote { remoteProcess.waitFor() }

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean =
        withRemote { remoteProcess.waitForTimeout(timeout, unit.toString()) }

    override fun exitValue(): Int = withRemote { remoteProcess.exitValue() }

    override fun isAlive(): Boolean = alive()

    override fun destroy() {
        withRemote { remoteProcess.destroy() }
    }

    fun alive(): Boolean = withRemote { remoteProcess.alive() }

    fun asBinder(): IBinder = remoteProcess.asBinder()

    private inline fun <T> withRemote(block: () -> T): T =
        try {
            block()
        } catch (e: RemoteException) {
            throw RuntimeException("Shizuku remote process call failed", e)
        }
}
