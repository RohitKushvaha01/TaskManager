package com.rk.taskmanager.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.rk.taskmanager.TaskManager
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs

object ShizukuUtil {
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

    fun withService(ServiceCallback: Error.(TaskManagerService?)-> Unit){
        if (isShizukuRunning()){
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
                                ServiceCallback.invoke(Error.NO_ERROR,TaskManagerService.CREATOR.asInterface(service!!))
                            }

                            override fun onServiceDisconnected(name: ComponentName?) {
                                Log.d("ShizukuService", "Service disconnected")
                            }
                        }
                    )
                }.onFailure {
                    ServiceCallback.invoke(Error.UNKNOWN_ERROR,null)
                }


            }
        }else{
            ServiceCallback.invoke(Error.SHIZUKU_NOT_RUNNNING,null)
        }
    }

}