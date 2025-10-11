package com.rk.taskmanager.shizuku

import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.ui.util.fastJoinToString
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
        return Shizuku.getBinder() != null && Shizuku.pingBinder()
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
        return Shizuku.getUid() == 0
    }

    fun isShell(): Boolean{
        return Shizuku.getUid() == 2000
    }

    fun isShizukuInstalled(): Boolean {
        return try {
            TaskManager.getContext().packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
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

                println("exec begin")
                val result: ShizukuRemoteProcess? =
                    checkNotNull(method.invoke(null, cmd, env, dir) as ShizukuRemoteProcess?)

                println("waiting")
                result!!.waitFor()
                println("done")
                println("exitCode ${result.exitValue()}")

                Log.e("Shizuku_newProcess",result.errorStream.bufferedReader().readLines().toString())

                Pair(result.exitValue(),result.inputStream.bufferedReader().readLines().fastJoinToString("\n"))
            }catch (e: Exception){
                e.printStackTrace()
                Pair(-1,e.message.toString())
            }
        }
}
