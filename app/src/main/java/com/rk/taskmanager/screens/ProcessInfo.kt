package com.rk.taskmanager.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.rk.components.TextCard
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.daemon_messages
import com.rk.send_daemon_messages
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.R
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.shizuku.ShizukuUtil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.reflect.Method
import kotlin.math.round
import kotlin.math.roundToInt


fun isAppInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

fun getApkNameFromPackage(context: Context, packageName: String): String? {
    return try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName,
            PackageManager.GET_META_DATA)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

fun isSystemApp(context: Context, packageName: String): Boolean {
    return try {
        val packageManager = context.packageManager
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (e: PackageManager.NameNotFoundException) {
        // Package not found
        false
    }
}


fun getAppIcon(context: Context, packageName: String): Drawable? {
    return try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationIcon(appInfo)
    } catch (e: PackageManager.NameNotFoundException) {
        null // App not found
    }
}

fun drawableTobitMap(drawable: Drawable?): Bitmap?{
    return drawable?.let {
        if (it is BitmapDrawable) {
            it.bitmap
        } else {
            val bitmap = Bitmap.createBitmap(it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            it.setBounds(0, 0, canvas.width, canvas.height)
            it.draw(canvas)
            bitmap
        }
    }
}

fun getAppIconBitmap(context: Context, packageName: String): Bitmap? {
    val drawable = getAppIcon(context, packageName)
    return drawableTobitMap(drawable)
}




@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    DelicateCoroutinesApi::class
)
@Composable
fun ProcessInfo(
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: ProcessViewModel,
    pid: Int
) {
    var proc = remember { mutableStateOf<ProcessViewModel.Process?>(null) }
    var username = remember { mutableStateOf("Unknown") }
    var kill_result = remember { mutableStateOf<Boolean?>(null) }
    var killing = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isApk = remember { mutableStateOf<Boolean>(false) }
    var isApkForceStopped = remember { mutableStateOf<Boolean?>(false) }


    LaunchedEffect(Unit) {
        proc.value = withContext(Dispatchers.IO) {
            delay(700)
            viewModel.processes.toList().find { it.pid == pid }
        }
        if (proc.value != null) {
            username.value = getUsernameFromUid(proc.value!!.uid) ?: proc.value!!.uid.toString()
        }
        isApk.value = isAppInstalled(TaskManager.getContext(), proc.value!!.cmdLine)
    }

    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        TopAppBar(title = {
            Text("ProcessInfo")
        }, navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = "go back"
                )
            }
        })
    }) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding), contentAlignment = Alignment.Center) {
            if (proc.value == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(modifier.verticalScroll(rememberScrollState())) {
                    PreferenceGroup {
                        val enabled = proc.value!!.pid > 1
                        val interactionSource = remember { MutableInteractionSource() }
                        PreferenceTemplate(
                            modifier = modifier
                                .combinedClickable(
                                enabled = enabled,
                                indication = ripple(),
                                interactionSource = interactionSource,
                                onClick = {
                                    if ((kill_result.value != true) && !killing.value){
                                        GlobalScope.launch(Dispatchers.IO) {
                                            killing.value = true

                                            runCatching {

                                                launch {
                                                    daemon_messages.collect { message ->
                                                        if (message.startsWith("KILL_RESULT:")){
                                                            kill_result.value = message.removePrefix("KILL_RESULT:").toBoolean()
                                                            killing.value = false
                                                        }
                                                    }
                                                }

                                                if (isApk.value && ShizukuUtil.isShell()){
                                                    send_daemon_messages.emit("FORCE_STOP:${proc.value!!.cmdLine}")
                                                }else{
                                                    send_daemon_messages.emit("KILL:${proc.value!!.pid}")
                                                }


                                                viewModel.refreshProcesses()
                                            }.onFailure {
                                                it.printStackTrace()
                                            }
                                        }
                                    }
                                }
                            ),
                            contentModifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 16.dp)
                                .padding(start = 16.dp),
                            title = {
                                Text(
                                    fontWeight = FontWeight.Bold,
                                    text = if (killing.value){if (isApk.value == true){"Stopping..."}else{"Killing..."}}else{
                                        kill_result.value?.let {
                                            if (it) {
                                                if (isApk.value){"Killed"}else{"Stopped"}
                                            } else {
                                                if (ShizukuUtil.isShell() && !isApk.value){
                                                    "Kill failed (Permission denied)"
                                                }else{
                                                    "Kill failed (try again?)"
                                                }
                                            }
                                        } ?: if (isApk.value){"Force Stop"}else{"Kill"}
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            description = { Text("Kill Process") },
                            enabled = enabled,
                            applyPaddings = false,
                            endWidget = null,
                            startWidget = {
                                if (killing.value) {
                                    CircularProgressIndicator(modifier = Modifier
                                        .padding(start = 16.dp)
                                        .alpha(if (enabled) 1f else 0.3f),)
                                } else {
                                    kill_result.value?.let {
                                        if (it) {
                                            Icon(
                                                modifier = Modifier
                                                    .padding(start = 16.dp)
                                                    .alpha(if (enabled) 1f else 0.3f),
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null
                                            )
                                        } else {
                                            Icon(
                                                modifier = Modifier
                                                    .padding(start = 16.dp)
                                                    .alpha(if (enabled) 1f else 0.3f),
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = null
                                            )
                                        }
                                    } ?: Icon(
                                        modifier = Modifier
                                            .padding(start = 16.dp)
                                            .alpha(if (enabled) 1f else 0.3f),
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                    }

                    PreferenceGroup {
                        var name by remember { mutableStateOf("Loading") }


                        LaunchedEffect(Unit) {
                            name = getApkNameFromPackage(TaskManager.getContext(), proc.value!!.cmdLine) ?: proc.value!!.name
                        }

                        TextCard(text = "Name", description = name.trim())
                        TextCard(text = "PID", description = proc.value!!.pid.toString())
                        TextCard(
                            text = if(isApk.value){"Package"}else{"Command"},
                            description = proc.value!!.cmdLine.ifEmpty {
                                "No Command"
                            }
                        )
                        TextCard(text = "User", description = username.value)
                        if (proc.value!!.parentPid != 0) {
                            TextCard(
                                text = "Parent PID",
                                description = proc.value!!.parentPid.toString()
                            )
                        }
                        TextCard(text = "CPU Usage", description = proc.value!!.cpuUsage.roundToInt().toString() + "% (estimated)")
                        TextCard(
                            text = "Foreground",
                            description = proc.value!!.isForeground.toString()
                        )

                        fun formatSize(kb: Long): String {
                            return if (kb >= 1000) {
                                val mb = kb / 1024f
                                String.format(java.util.Locale.US,"%.2f MB", mb)
                            } else {
                                "$kb KB"
                            }
                        }

                        TextCard(
                            text = "RAM Usage",
                            description = formatSize(proc.value!!.memoryUsageKb)
                        )

                        if (proc.value!!.residentSetSizeKb != proc.value!!.memoryUsageKb) {
                            TextCard(
                                text = "Actual Ram Usage (RSS)",
                                description = formatSize(proc.value!!.residentSetSizeKb)
                            )
                        }


                        TextCard(
                            text = "Niceness",
                            description = "${proc.value!!.nice}"
                        )

                        TextCard(
                            text = "Status",
                            description = when (proc.value!!.state.toString()
                                .toLowerCase(Locale.current)) {
                                "r" -> "Running"
                                "s" -> "Sleeping"
                                "d" -> "Uninterruptible sleep"
                                "z" -> "Sleeping"
                                "t" -> "Stopped"
                                "x" -> "Dead"
                                else -> "Unknown"
                            }
                        )

                        TextCard(text = "Threads", description = proc.value!!.threads.toString())
                        TextCard(
                            text = "Start Time",
                            description = proc.value!!.startTime.toString()
                        )
                        TextCard(
                            text = "Elapsed Time",
                            description = proc.value!!.elapsedTime.toString()
                        )


                        TextCard(
                            text = "Virtual Memory",
                            description = "${proc.value!!.virtualMemoryKb}KB"
                        )

                        TextCard(text = "Cgroup", description = proc.value!!.cgroup)
                        if (proc.value!!.executablePath != "null") {
                            TextCard(text = "Executable", description = proc.value!!.executablePath)
                        }


                    }

                    Spacer(modifier = Modifier.padding(16.dp))
                }
            }
        }

    }
}


suspend fun getUsernameFromUid(uid: Int): String? = withContext(Dispatchers.IO) {
    return@withContext try {
        val process = ProcessBuilder("id", "-nu", uid.toString()).start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        reader.readLine()?.trim()
    } catch (e: Exception) {
        null
    }
}