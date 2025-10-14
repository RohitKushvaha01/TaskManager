package com.rk.taskmanager.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.navigation.NavController
import com.rk.components.SettingsToggle
import com.rk.components.TextCard
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.daemon_messages
import com.rk.send_daemon_messages
import com.rk.taskmanager.ProcessUiModel
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.getString
import com.rk.taskmanager.strings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


// Get ticks per second (usually 100 on Linux)
fun ticksPerSecond(): Long = Os.sysconf(OsConstants._SC_CLK_TCK)


// Calculate elapsed time from startTime ticks
fun elapsedFromStartTime(startTimeTicks: Long): String {
    val processStartMillis = startTimeToMillis(startTimeTicks)
    val now = System.currentTimeMillis()
    val elapsedSeconds = (now - processStartMillis) / 1000

    val h = TimeUnit.SECONDS.toHours(elapsedSeconds)
    val m = TimeUnit.SECONDS.toMinutes(elapsedSeconds) % 60
    val s = elapsedSeconds % 60

    return String.format("%02d:%02d:%02d", h, m, s)
}

// Convert process start time (ticks since boot) → wall clock time (ms since epoch)
fun startTimeToMillis(startTimeTicks: Long): Long {
    val ticksPerSecond = sysconf() // custom helper below
    val bootTimeMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    val processStartMillis = bootTimeMillis + (startTimeTicks * 1000 / ticksPerSecond)
    return processStartMillis
}

// Convert elapsed ticks → duration string
fun elapsedTimeToString(elapsedTicks: Long): String {
    val ticksPerSecond = sysconf()
    val seconds = elapsedTicks / ticksPerSecond
    val h = TimeUnit.SECONDS.toHours(seconds)
    val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val s = seconds % 60
    return String.format(java.util.Locale.ENGLISH, "%02d:%02d:%02d", h, m, s)
}

// This mimics sysconf(_SC_CLK_TCK) ≈ 100 on Linux
fun sysconf(): Long {
    return Os.sysconf(OsConstants._SC_CLK_TCK)
}

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
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            )
        ).toString()
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

fun drawableTobitMap(drawable: Drawable?): Bitmap? {
    return drawable?.let {
        if (it is BitmapDrawable) {
            it.bitmap
        } else {
            val bitmap = createBitmap(it.intrinsicWidth, it.intrinsicHeight)
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


suspend fun killProc(proc: ProcessViewModel.Process): Boolean {
    var killResult = false

    val isApk = isAppInstalled(TaskManager.requireContext(), proc.cmdLine)


    killResult = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(3000L) {
                val resultDeferred = async {
                    daemon_messages.first { message ->
                        message.startsWith("KILL_RESULT:")
                    }.removePrefix("KILL_RESULT:").toBoolean()
                }

                // Send kill command
                if (isApk) {
                    send_daemon_messages.emit("FORCE_STOP:${proc.cmdLine}")
                } else {
                    send_daemon_messages.emit("KILL:${proc.pid}")
                }

                // Wait for result
                resultDeferred.await()
            }
        }.onFailure {
            it.printStackTrace()
        }.getOrDefault(false)
    }

    return killResult
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
    val proc = remember { mutableStateOf<ProcessUiModel?>(null) }
    val username = remember { mutableStateOf("Unknown") }
    val scope = rememberCoroutineScope()
    val isApk = remember { mutableStateOf<Boolean>(false) }
    val cpuUsage = remember { mutableIntStateOf(-1) }
    val uiProcesses by viewModel.uiProcesses.collectAsState()

    LaunchedEffect(Unit) {
        proc.value = withContext(Dispatchers.IO) {
            uiProcesses.find { it.proc.pid == pid }
        }

        if(proc.value != null){
            username.value =
                getUsernameFromUid(proc.value!!.proc.uid) ?: proc.value!!.proc.uid.toString()
            isApk.value = isAppInstalled(TaskManager.getContext(), proc.value!!.proc.cmdLine)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        TopAppBar(title = {
            Text(stringResource(strings.proc_info))
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
                        val enabled = proc.value!!.proc.pid > 1 && proc.value!!.killed.value.not()
                        val interactionSource = remember { MutableInteractionSource() }
                        PreferenceTemplate(
                            modifier = modifier
                                .combinedClickable(
                                    enabled = enabled,
                                    indication = ripple(),
                                    interactionSource = interactionSource,
                                    onClick = {
                                        scope.launch {
                                            proc.value!!.killing.value = true
                                            proc.value!!.killed.value = killProc(proc.value!!.proc)
                                            delay(300)
                                            proc.value!!.killing.value = false
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
                                    text =
                                        if (proc.value!!.killing.value) {
                                            stringResource(
                                                if (isApk.value) {
                                                    strings.stopping
                                                } else {
                                                    strings.killing
                                                }
                                            )
                                        } else {
                                            if (proc.value!!.killed.value!!) {
                                                stringResource(
                                                    if (isApk.value) {
                                                        strings.killed
                                                    } else {
                                                        strings.stopped
                                                    }
                                                )
                                            } else {
                                                stringResource(strings.kill)
                                            }
                                        },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            description = { Text(stringResource(strings.kill_proc)) },
                            enabled = enabled,
                            applyPaddings = false,
                            endWidget = null,
                            startWidget = {
                                if (proc.value!!.killing.value) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(start = 16.dp)
                                            .alpha(if (enabled) 1f else 0.3f),
                                    )
                                } else {
                                    if (proc.value!!.killed.value) {
                                        Icon(
                                            modifier = Modifier
                                                .padding(start = 16.dp)
                                                .alpha(if (enabled) 1f else 0.3f),
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null
                                        )
                                    }else{
                                        Icon(
                                            modifier = Modifier
                                                .padding(start = 16.dp)
                                                .alpha(if (enabled) 1f else 0.3f),
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = null
                                        )
                                    }

                                }
                            }
                        )
                    }

                    PreferenceGroup {
                        var name by remember { mutableStateOf(strings.loading.getString()) }


                        LaunchedEffect(Unit) {
                            name = getApkNameFromPackage(
                                TaskManager.requireContext(),
                                proc.value!!.proc.cmdLine
                            ) ?: proc.value!!.proc.name
                        }

                        TextCard(text = stringResource(strings.name), description = name.trim())
                        TextCard(text = "PID", description = proc.value!!.proc.pid.toString())
                        TextCard(
                            text = stringResource(
                                if (isApk.value) {
                                    strings.str_package
                                } else {
                                    strings.command
                                }
                            ),
                            description = proc.value!!.proc.cmdLine.ifEmpty {
                                stringResource(strings.no_cmd)
                            }
                        )
                        TextCard(text = stringResource(strings.user), description = username.value)
                        if (proc.value!!.proc.parentPid != 0) {

                            val text = stringResource(strings.parent_pid)
                            val description = proc.value!!.proc.parentPid.toString()
                            SettingsToggle(
                                label = text,
                                description = description,
                                default = false,
                                showSwitch = false,
                                onLongClick = {
                                    val clipboard = TaskManager.requireContext()
                                        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(text, description)
                                    clipboard.setPrimaryClip(clip)

                                    Toast.makeText(
                                        TaskManager.requireContext(),
                                        strings.copied.getString(),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                endWidget = {
                                    Icon(
                                        modifier = Modifier.padding(end = 16.dp),
                                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                },
                                sideEffect = {
                                    navController.navigate(
                                        SettingsRoutes.ProcessInfo.createRoute(
                                            proc.value!!.proc.parentPid
                                        )
                                    )
                                })

                        }

                        LaunchedEffect(Unit) {
                            daemon_messages.collect { message ->
                                if (message.startsWith("CPU_PID:")) {
                                    cpuUsage.intValue =
                                        message.removePrefix("CPU_PID:").toFloat().toInt()
                                }
                            }
                        }

                        LaunchedEffect(Unit) {
                            while (isActive) {
                                if (proc.value != null) {
                                    send_daemon_messages.emit("PING_PID_CPU:${proc.value!!.proc.pid}")
                                }
                                delay(1000)
                            }
                        }

                        TextCard(
                            text = stringResource(strings.cpu_usage),
                            description = (if (cpuUsage.intValue == -1) {
                                proc.value!!.proc.cpuUsage.roundToInt().toString()
                            } else {
                                cpuUsage.intValue
                            }).toString() + "% (${strings.estimated.getString()})"
                        )
                        TextCard(
                            text = stringResource(strings.is_foreground),
                            description = proc.value!!.proc.isForeground.toString()
                        )

                        fun formatSize(kb: Long): String {
                            return if (kb >= 1000) {
                                val mb = kb / 1024f
                                String.format(java.util.Locale.US, "%.2f MB", mb)
                            } else {
                                "$kb KB"
                            }
                        }

                        TextCard(
                            text = stringResource(strings.ram_usage),
                            description = formatSize(proc.value!!.proc.memoryUsageKb)
                        )

                        if (proc.value!!.proc.residentSetSizeKb != proc.value!!.proc.memoryUsageKb) {
                            TextCard(
                                text = stringResource(strings.actual_ram_usage),
                                description = formatSize(proc.value!!.proc.residentSetSizeKb)
                            )
                        }


                        TextCard(
                            text = stringResource(strings.niceness),
                            description = "${proc.value!!.proc.nice}"
                        )

                        TextCard(
                            text = stringResource(strings.status),
                            description = proc.value!!.proc.state
                        )

                        TextCard(
                            text = stringResource(strings.threads),
                            description = proc.value!!.proc.threads.toString()
                        )

                        TextCard(
                            text = stringResource(strings.start_time),
                            description = DateFormat.getDateTimeInstance().format(
                                Date(startTimeToMillis(proc.value!!.proc.startTime))
                            )
                        )

                        var elapsed by remember { mutableStateOf("") }

                        val startTimeTicks = proc.value!!.proc.startTime
                        LaunchedEffect(startTimeTicks) {
                            while (isActive) {
                                elapsed = elapsedFromStartTime(startTimeTicks)
                                delay(1000)
                            }
                        }

                        TextCard(
                            text = stringResource(strings.elapsed_time),
                            description = elapsed
                        )

                        if (proc.value!!.proc.executablePath != "null") {
                            TextCard(
                                text = stringResource(strings.exec_path),
                                description = proc.value!!.proc.executablePath
                            )
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