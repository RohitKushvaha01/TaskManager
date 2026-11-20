package com.rk.taskmanager.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.Keep
import androidx.lifecycle.lifecycleScope
import com.rk.taskmanager.MainActivity
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.daemon.DaemonUserService
import com.rk.taskmanager.daemon.IDaemonUserService
import com.rk.taskmanager.daemon.startDaemon
import com.rk.commons.settings.Settings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException



@OptIn(DelicateCoroutinesApi::class)
@Keep
object ShizukuShell {

    private const val TAG = "ShizukuShell"
    private const val SHIZUKU_PERMISSION_REQUEST_CODE = 93848

    init {
        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                MainActivity.instance?.let {
                    it.lifecycleScope.launch {
                        startDaemon(it, Settings.workingMode)
                    }
                }
            }
        }
    }

    fun isShizukuRunning(): Boolean{
        return try {
            Shizuku.getBinder() != null && Shizuku.pingBinder()
        }catch (e: Exception){
            e.printStackTrace()
            return false
        }
    }


    fun isPermissionGranted(): Boolean{
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }catch (e: Exception){
            e.printStackTrace()
            false
        }
    }

    fun isRoot(): Boolean{
        if (isShizukuRunning().not()){
            return false
        }
        return Shizuku.getUid() == 0
    }

    fun isShell(): Boolean{
        if (isShizukuRunning().not()){
            return false
        }
        return Shizuku.getUid() == 2000
    }

    fun isShizukuInstalled(): Boolean {
        return try {
            TaskManager.requireContext().packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }



    fun requestPermission(){
        if (isShizukuRunning().not()){
            return
        }
        if (isPermissionGranted()){
            return
        }

        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
    }

    /**
     * Legacy path: invokes the private `Shizuku.newProcess` method through reflection.
     *
     * Kept unchanged as a fallback for the existing "Shizuku" working mode. The method
     * is deprecated, private since Shizuku API 13 and scheduled for removal in API 14,
     * so new code should prefer [startStreamingProcessAidl].
     */
    @Keep
    @Throws(
        InvocationTargetException::class,
        IllegalAccessException::class,
        NoSuchMethodException::class
    )
    suspend fun startStreamingProcess(cmd: Array<String?>, env: Array<String?>?, dir: String?): ShizukuRemoteProcess =
        withContext(Dispatchers.IO) {

            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )

            method.isAccessible = true

            val result: ShizukuRemoteProcess? =
                checkNotNull(method.invoke(null, cmd, env, dir) as ShizukuRemoteProcess?)

            Log.e("Shizuku_startProcess", "Process started")
            return@withContext result!!
        }

    /**
     * Starts a process by calling the `newProcess` transaction of Shizuku's
     * `IShizukuService` AIDL interface directly.
     *
     * This is the non-deprecated replacement for [startStreamingProcess]: it does not
     * use reflection and only depends on the AIDL contract exposed by the Shizuku
     * server. The returned [Process] must be kept referenced while its streams are in
     * use, because the underlying `IRemoteProcess` binder keeps the remote process
     * alive.
     *
     * If a future Shizuku release removes this transaction, the fully supported
     * long-term replacement is [Shizuku.bindUserService] with a UserService running as
     * shell/root.
     */
    suspend fun startStreamingProcessAidl(
        cmd: Array<String>,
        env: Array<String>?,
        dir: String?
    ): Process = withContext(Dispatchers.IO) {
        val binder = checkNotNull(Shizuku.getBinder()) {
            "Shizuku binder is not available"
        }
        val service = checkNotNull(IShizukuService.Stub.asInterface(binder)) {
            "Shizuku service is not available"
        }
        val remoteProcess = checkNotNull(service.newProcess(cmd, env, dir)) {
            "Shizuku returned a null process"
        }

        Log.d(TAG, "Process started via IShizukuService.newProcess")
        ShizukuProcess(remoteProcess)
    }

    /**
     * Starts the daemon through a Shizuku UserService, the officially supported way
     * to run code as shell/root.
     *
     * The user service runs in Shizuku's app_process with shell/root UID, starts the
     * daemon binary and exposes its stdio through pipes; see [DaemonUserService].
     */
    suspend fun startStreamingProcessService(
        context: Context,
        daemonPath: String
    ): Process = withContext(Dispatchers.IO) {
        val args = Shizuku.UserServiceArgs(
            ComponentName(context, DaemonUserService::class.java)
        )
            .daemon(false)
            .processNameSuffix("taskmanagerd")
            .debuggable(false)
            .version(1)

        val bound = bindUserService(args)
        var appStdin: ParcelFileDescriptor? = null
        var appStdout: ParcelFileDescriptor? = null
        try {
            val stdinPipe = ParcelFileDescriptor.createPipe()
            val stdinRead = stdinPipe[0]
            appStdin = stdinPipe[1]

            val stdoutPipe = ParcelFileDescriptor.createPipe()
            appStdout = stdoutPipe[0]
            val stdoutWrite = stdoutPipe[1]

            try {
                bound.service.start(daemonPath, "/", stdinRead, stdoutWrite)
            } finally {
                // The user service received duplicates of these ends.
                runCatching { stdinRead.close() }
                runCatching { stdoutWrite.close() }
            }

            Log.d(TAG, "Process started via Shizuku UserService")
            ShizukuUserServiceProcess(
                service = bound.service,
                args = args,
                connection = bound.connection,
                stdin = checkNotNull(appStdin),
                stdout = checkNotNull(appStdout)
            )
        } catch (t: Throwable) {
            runCatching { appStdin?.close() }
            runCatching { appStdout?.close() }
            runCatching { bound.service.destroy() }
            runCatching { Shizuku.unbindUserService(args, bound.connection, true) }
            throw t
        }
    }

    private class BoundUserService(
        val service: IDaemonUserService,
        val connection: ServiceConnection
    )

    private suspend fun bindUserService(args: Shizuku.UserServiceArgs): BoundUserService =
        suspendCancellableCoroutine { continuation ->
            var resumed = false
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    if (resumed) return
                    resumed = true

                    if (!continuation.isActive) {
                        runCatching { Shizuku.unbindUserService(args, this, false) }
                        return
                    }

                    val service = IDaemonUserService.Stub.asInterface(binder)
                    if (service == null) {
                        continuation.resumeWithException(
                            IllegalStateException("Shizuku user service returned a null binder")
                        )
                    } else {
                        continuation.resume(BoundUserService(service, this))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    if (resumed) return
                    resumed = true
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("Shizuku user service disconnected")
                        )
                    }
                }
            }

            continuation.invokeOnCancellation {
                runCatching { Shizuku.unbindUserService(args, connection, false) }
            }

            try {
                Shizuku.bindUserService(args, connection)
            } catch (t: Throwable) {
                resumed = true
                if (continuation.isActive) {
                    continuation.resumeWithException(t)
                }
            }
        }
}
