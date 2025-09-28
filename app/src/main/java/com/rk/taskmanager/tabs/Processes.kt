package com.rk.taskmanager.tabs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.rk.components.SettingsToggle
import com.rk.components.XedDialog
import com.rk.components.compose.preferences.base.DividerColumn
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.taskmanager.ProcessUiModel
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.screens.drawableTobitMap
import com.rk.taskmanager.screens.getApkNameFromPackage
import com.rk.taskmanager.screens.getAppIcon
import com.rk.taskmanager.screens.getAppIconBitmap
import com.rk.taskmanager.screens.isAppInstalled
import com.rk.taskmanager.screens.isSystemApp
import com.rk.taskmanager.screens.showFilter
import com.rk.taskmanager.screens.showLinuxProcess
import com.rk.taskmanager.screens.showSystemApps
import com.rk.taskmanager.settings.Settings
import com.rk.taskmanager.shizuku.Proc
import com.rk.taskmanager.shizuku.ShizukuUtil
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Processes(
    modifier: Modifier = Modifier,
    viewModel: ProcessViewModel,
    navController: NavController
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    if (showFilter.value){
        XedDialog(onDismissRequest = {showFilter.value = false}) {
            DividerColumn {
                SettingsToggle(label = "Show System Apps", description = null, showSwitch = true, default = showSystemApps.value, sideEffect = {
                    scope.launch{
                        Settings.showSystemApps = it
                        showSystemApps.value = it
                    }

                })

                SettingsToggle(label = "Show Linux Processes", description = null, showSwitch = true, default = showLinuxProcess.value, sideEffect = {
                    scope.launch{
                        Settings.showLinuxProcess = it
                        showLinuxProcess.value = it
                    }
                })
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshAuto()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

            when (state) {
                ShizukuUtil.Error.NOT_INSTALLED -> {
                    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        val context = LocalContext.current
                        Text("Shizuku not installed")
                        Button(onClick = {
                            openPlayStore(context,"moe.shizuku.privileged.api")
                        }) {
                            Text("Install")
                        }
                    }
                }

                ShizukuUtil.Error.NO_ERROR -> {
                    if (viewModel.isLoading.value) {
                        CircularProgressIndicator()
                    } else if (viewModel.processes.isEmpty()){
                        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Unable to read process list from /proc")
                            Button(onClick = {
                                viewModel.refreshProcesses()
                            }) {
                                Text("Refresh")
                            }
                        }

                    } else {
                        PullToRefreshBox(isRefreshing = viewModel.isLoading.value, onRefresh = {
                            viewModel.refreshProcesses()
                        }) {
                            val listState = rememberLazyListState()

                            val uiProcesses by viewModel.uiProcesses.collectAsState()

                            val filteredProcesses = remember(uiProcesses, showSystemApps.value, showLinuxProcess.value) {
                                uiProcesses.filter { process ->
                                    when {
                                        process.isSystemApp && !showSystemApps.value -> false
                                        !process.isInstalledApp && !showLinuxProcess.value -> false
                                        else -> true
                                    }
                                }
                            }


                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState
                            ) {
                                items(filteredProcesses, key = { it.proc.pid }) { uiProc ->
                                    ProcessItem(modifier, uiProc, navController = navController)
                                }

                                item {
                                    Spacer(modifier = Modifier.padding(bottom = 32.dp))
                                }
                            }
                        }
                    }
                }

                ShizukuUtil.Error.SHIZUKU_NOT_RUNNNING -> {
                    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        val context = LocalContext.current
                        Text("Shizuku not started")
                        Button(onClick = {
                            launchShizuku(context = context)
                        }) {
                            Text("Open Shizuku")
                        }
                    }
                }

                ShizukuUtil.Error.PERMISSION_DENIED -> {
                    Text("Shizuku permission denied")
                }

                ShizukuUtil.Error.SHIZUKU_TIMEOUT -> {
                    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        val context = LocalContext.current
                        Text("Shizuku not running (connection timeout)")
                        Button(onClick = {
                            launchShizuku(context = context)
                        }) {
                            Text("Open Shizuku")
                        }
                    }
                }

                else -> {
                    Text("Unknown error, check logcat")
                }
            }


    }
}

const val textLimit = 40

@Composable
fun ProcessItem(
    modifier: Modifier,
    uiProc: ProcessUiModel,
    navController: NavController
) {

    PreferenceTemplate(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                indication = ripple(),
                interactionSource = remember { MutableInteractionSource() },
                onClick = { navController.navigate(SettingsRoutes.ProcessInfo.createRoute(uiProc.proc.pid)) }
            ),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = {
            Text(
                fontWeight = FontWeight.Bold,
                text = if (uiProc.name.length > textLimit) {
                    uiProc.name.take(textLimit) + "..."
                } else uiProc.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        description = {
            Text(uiProc.proc.cmdLine.removePrefix("/system/bin/").take(textLimit))
        },
        enabled = true,
        applyPaddings = false,
        startWidget = {
            if (uiProc.icon != null) {
                Image(
                    bitmap = uiProc.icon,
                    contentDescription = "App Icon",
                    modifier = Modifier.padding(start = 16.dp).size(24.dp),
                )
            } else {
                val fallbackId = when {
                    uiProc.proc.cmdLine.startsWith("/vendor") || uiProc.proc.cmdLine.isEmpty() ->
                        com.rk.taskmanager.R.drawable.cpu_24px

                    uiProc.proc.cmdLine.startsWith("/data/local/tmp") || uiProc.proc.uid == 2000 ->
                        com.rk.taskmanager.R.drawable.usb_24px

                    else ->
                        com.rk.taskmanager.R.drawable.ic_android_black_24dp
                }

                Image(
                    painter = painterResource(id = fallbackId),
                    contentDescription = "Fallback Icon",
                    modifier = Modifier.padding(start = 16.dp).size(24.dp),
                )
            }
        }

    )
}



fun launchShizuku(context: Context) {
    val packageName = "moe.shizuku.privileged.api"

    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            Log.d("Shizuku", "Successfully launched Shizuku")
        } else {
            Toast.makeText(context, "Shizuku is not installed or has no launch activity", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e("Shizuku", "Error launching Shizuku: ${e.message}")
        Toast.makeText(context, "Failed to launch Shizuku: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun openPlayStore(context: Context, packageName: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Play Store not available, open web version
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        context.startActivity(intent)
    }
}