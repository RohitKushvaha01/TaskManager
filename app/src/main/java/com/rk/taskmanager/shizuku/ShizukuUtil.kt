package com.rk.taskmanager.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.rk.taskmanager.TaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import java.lang.ref.WeakReference

object ShizukuUtil {
    var serviceBinder = WeakReference<TaskManagerService?>(null)


    const val SHIZUKU_PERMISSION_REQUEST_CODE = 872837
    var callback:((Boolean)-> Unit)? = null


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
        return Shizuku.pingBinder()
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
    
    enum class Error{
        PERMISSION_DENIED,
        SHIZUKU_NOT_RUNNNING,
        UNKNOWN_ERROR,
        NO_ERROR,
    }

    private var isWaiting = false
    suspend fun withService(ServiceCallback: Error.(TaskManagerService?)-> Unit) = withContext(Dispatchers.IO){
        if (isShizukuRunning()){

            while (isWaiting && serviceBinder.get() == null){
                delay(100)
            }

            if (serviceBinder.get() != null){
                ServiceCallback.invoke(Error.NO_ERROR,serviceBinder.get()!!)
                return@withContext
            }

            requestPermission { granted ->
                if (granted.not()){
                    ServiceCallback.invoke(Error.PERMISSION_DENIED,null)
                    return@requestPermission
                }

                runCatching {
                    Shizuku.bindUserService(
                        UserServiceArgs(ComponentName(TaskManager.Companion.getContext().packageName, TaskManagerServiceImpl::class.java.name))
                            .daemon(false)
                            .debuggable(true)
                            .processNameSuffix("task_manager")
                            .version(1),
                        object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                                serviceBinder = WeakReference(TaskManagerService.CREATOR.asInterface(service!!))
                                ServiceCallback.invoke(Error.NO_ERROR,serviceBinder.get()!!)
                            }

                            override fun onServiceDisconnected(name: ComponentName?) {
                                Log.d("ShizukuService", "Service disconnected")
                            }
                        }
                    )
                    isWaiting = true
                }.onFailure {
                    ServiceCallback.invoke(Error.UNKNOWN_ERROR,null)
                }


            }
        }else{
            ServiceCallback.invoke(Error.SHIZUKU_NOT_RUNNNING,null)
        }
    }

}