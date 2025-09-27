package com.rk.taskmanager.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.annotation.Keep
import com.rk.taskmanager.TaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import java.lang.ref.WeakReference

@Keep
object ShizukuUtil {
    var serviceBinder: TaskManagerService? = null


    const val SHIZUKU_PERMISSION_REQUEST_CODE = 872837
    var callback:((Boolean)-> Unit)? = null
    val isShizukuInstalled = isShizukuInstalled(TaskManager.getContext())


    init {
        Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener{
            override fun onRequestPermissionResult(
                requestCode: Int,
                grantResult: Int
            ) {
                if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE){
                    callback?.invoke(grantResult == PackageManager.PERMISSION_GRANTED)
                    callback = null
                }else{
                    println("Unknown request code")
                }
            }
        })
    }

    fun isShizukuRunning(): Boolean{
        return Shizuku.pingBinder() && Shizuku.getBinder() != null
    }

    fun isPermissionGranted(): Boolean{
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission(callback:(Boolean)-> Unit){
        if (isPermissionGranted()){
            callback(true)
            return
        }

        this.callback = callback
        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
    }

    fun isRoot(): Boolean{
        return Shizuku.getUid() == 0
    }

    fun isShell(): Boolean{
        return Shizuku.getUid() == 2000
    }

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    enum class Error{
        PERMISSION_DENIED,
        NOT_INSTALLED,
        SHIZUKU_NOT_RUNNNING,
        UNKNOWN_ERROR,
        NO_ERROR,
        SHIZUKU_TIMEOUT
    }

    private var isWaiting = false

    suspend fun withService(ServiceCallback: suspend Error.(TaskManagerService?)-> Unit) = withContext(Dispatchers.IO){
        if (!isShizukuInstalled){
            ServiceCallback.invoke(Error.NOT_INSTALLED,null)
            return@withContext
        }

        val context = this
        if (isShizukuRunning()){
            runCatching {
                var timeMs = 0L
                while (isWaiting && serviceBinder == null){
                    delay(300)
                    timeMs += 300
                    println("waiting...")
                    if (timeMs > 5000){
                        timeMs = 0
                        ServiceCallback.invoke(Error.SHIZUKU_TIMEOUT,null)
                        return@withContext
                    }
                }

                if (serviceBinder != null){
                    ServiceCallback.invoke(Error.NO_ERROR,serviceBinder!!)
                    return@withContext
                }

                requestPermission { granted ->
                    if (granted.not()){
                        context.launch{
                            ServiceCallback.invoke(Error.PERMISSION_DENIED,null)
                        }
                        return@requestPermission
                    }

                    runCatching {
                        isWaiting = true
                        Shizuku.bindUserService(
                            UserServiceArgs(ComponentName(TaskManager.Companion.getContext().packageName,
                                TaskManagerBackend::class.java.name))
                                .daemon(true)
                                .processNameSuffix("task_manager")
                                .version(2),
                            object : ServiceConnection {
                                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                                    serviceBinder = TaskManagerService.CREATOR.asInterface(service!!)
                                    isWaiting = false
                                    context.launch{
                                        ServiceCallback.invoke(Error.NO_ERROR,serviceBinder!!)
                                    }
                                }

                                override fun onServiceDisconnected(name: ComponentName?) {
                                    Log.d("ShizukuService", "Service disconnected")
                                }
                            }
                        )
                    }.onFailure {
                        context.launch{
                            ServiceCallback.invoke(Error.UNKNOWN_ERROR,null)
                        }
                    }


                }

            }.onFailure {
                it.printStackTrace()
                ServiceCallback.invoke(Error.UNKNOWN_ERROR,null)
            }
        }else{
            ServiceCallback.invoke(Error.SHIZUKU_NOT_RUNNNING,null)
        }
    }

}