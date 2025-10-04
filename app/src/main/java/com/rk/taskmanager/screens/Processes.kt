package com.rk.taskmanager.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.SettingsToggle
import com.rk.components.XedDialog
import com.rk.components.compose.preferences.base.DividerColumn
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.taskmanager.ProcessUiModel
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.R
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.settings.Settings
import com.rk.taskmanager.shizuku.ShizukuUtil
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Processes(
    modifier: Modifier = Modifier,
    viewModel: ProcessViewModel,
    navController: NavController
) {
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
                        R.drawable.cpu_24px

                    uiProc.proc.cmdLine.startsWith("/data/local/tmp") || uiProc.proc.uid == 2000 ->
                        R.drawable.usb_24px

                    else ->
                        R.drawable.ic_android_black_24dp
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