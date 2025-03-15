package com.rk.taskmanager.screens

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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.TextCard
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.shizuku.Proc
import com.rk.taskmanager.shizuku.SP
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
    var proc = remember { mutableStateOf<Proc?>(null) }
    var username = remember { mutableStateOf("Unknown") }
    var kill_result = remember { mutableStateOf<Boolean?>(null) }
    var killing = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        proc.value = withContext(Dispatchers.IO) {
            delay(700)
            viewModel.processes.toList().find { it.pid == pid }
        }
        if (proc.value != null) {
            username.value = getUsernameFromUid(proc.value!!.uid) ?: proc.value!!.uid.toString()
        }

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
                            modifier = modifier.combinedClickable(
                                enabled = enabled,
                                indication = ripple(),
                                interactionSource = interactionSource,
                                onClick = {
                                    if ((kill_result.value != true) && !killing.value){
                                        val pid = proc.value!!.pid
                                        val signal = 9
                                        GlobalScope.launch(Dispatchers.IO) {
                                            killing.value = true

                                            val cmd: Array<String> = arrayOf("kill", "-$signal", pid.toString())
                                            val result = SP.newProcess(cmd,emptyArray<String>(),"/")
                                            println(result)
                                            kill_result.value = result == 0
                                            killing.value = false
                                            if (kill_result.value == true){
                                                viewModel.processes.remove(proc.value)
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
                                    text = if (killing.value){"Killing..."}else{
                                        kill_result.value?.let {
                                            if (it) {
                                                "Killed"
                                            } else {
                                                if (ShizukuUtil.isShell()){
                                                    "Kill failed (Permission denied)"
                                                }else{
                                                    "Kill failed (try again?)"
                                                }
                                            }
                                        } ?: "Kill"
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
                                    CircularProgressIndicator(modifier = Modifier.padding(start = 16.dp).alpha(if (enabled) 1f else 0.3f),)
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
                                                imageVector = Icons.Rounded.ErrorOutline,
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
                        TextCard(text = "Name", description = proc.value!!.name.trimEnd())
                        TextCard(text = "PID", description = proc.value!!.pid.toString())
                        TextCard(
                            text = "Command",
                            description = if (proc.value!!.cmdLine.isEmpty()) {
                                "No Command"
                            } else {
                                proc.value!!.cmdLine
                            }
                        )
                        TextCard(text = "User", description = username.value)
                        if (proc.value!!.parentPid != 0) {
                            TextCard(
                                text = "Parent PID",
                                description = proc.value!!.parentPid.toString()
                            )
                        }
                        TextCard(text = "CPU Usage", description = proc.value!!.cpuUsage.toString())
                        TextCard(
                            text = "Foreground",
                            description = proc.value!!.isForeground.toString()
                        )
                        TextCard(
                            text = "RAM Usage",
                            description = "${proc.value!!.memoryUsageKb}KB"
                        )
                        TextCard(
                            text = "Nice",
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
                            text = "Actual Ram Usage (RSS)",
                            description = "${proc.value!!.residentSetSizeKb}KB"
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