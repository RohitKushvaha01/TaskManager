package com.rk.taskmanager.shizuku

import android.content.pm.PackageManager
import androidx.annotation.Keep
import androidx.lifecycle.lifecycleScope
import com.rk.startDaemon
import com.rk.taskmanager.MainActivity
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.settings.Settings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.lang.reflect.InvocationTargetException



@OptIn(DelicateCoroutinesApi::class)
@Keep
object ShizukuShell {

    private const val SHIZUKU_PERMISSION_REQUEST_CODE = 93848
    val permissionFlow = MutableStateFlow(isPermissionGranted())

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
        return Shizuku.pingBinder() && Shizuku.getBinder() != null
    }


    fun isPermissionGranted(): Boolean{
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun isRoot(): Boolean{
        return Shizuku.getUid() == 0
    }

    fun isShell(): Boolean{
        return Shizuku.getUid() == 2000
    }



    fun requestPermission(){
        if (isPermissionGranted()){
            return
        }

        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
    }

    @Keep
    @Throws(
        InvocationTargetException::class,
        IllegalAccessException::class,
        NoSuchMethodException::class,
        InterruptedException::class
    )
    suspend fun newProcess(cmd: Array<String?>, env: Array<String?>?, dir: String?): Pair<Int,String> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,  // Java String[]
                    Array<String>::class.java,  // Java String[]
                    String::class.java // Java String
                )

                // Make it accessible
                method.isAccessible = true


                // Call the method
                val result: ShizukuRemoteProcess? =
                    checkNotNull(method.invoke(null, cmd, env, dir) as ShizukuRemoteProcess?)

                result!!.waitFor()

                Pair(result.exitValue(),result.inputStream.bufferedReader().readLine())
            }catch (e: Exception){
                e.printStackTrace()
                Pair(-1,e.message.toString())
            }
        }
}
