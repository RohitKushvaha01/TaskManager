package com.rk.taskmanager.tabs

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.screens.drawableTobitMap
import com.rk.taskmanager.screens.getApkNameFromPackage
import com.rk.taskmanager.screens.getAppIcon
import com.rk.taskmanager.screens.getAppIconBitmap
import com.rk.taskmanager.screens.isAppInstalled
import com.rk.taskmanager.shizuku.Proc
import com.rk.taskmanager.shizuku.ShizukuUtil


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Processes(
    modifier: Modifier = Modifier,
    viewModel: ProcessViewModel,
    navController: NavController
) {
    val state by viewModel.state.collectAsState()


    LaunchedEffect(Unit) {
        viewModel.refreshAuto()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            ShizukuUtil.Error.NO_ERROR -> {
                if (viewModel.isLoading.value && viewModel.processes.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    val listState = rememberLazyListState()
                    PullToRefreshBox(isRefreshing = viewModel.isLoading.value, onRefresh = {
                        viewModel.refreshProcesses()
                    }) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState
                        ) {
                            items(viewModel.processes.size, key = { viewModel.processes[it].pid }) { index ->
                                ProcessItem(modifier.padding(horizontal = 16.dp),viewModel.processes[index],navController = navController)
                            }

                            item(key = null) {
                                Spacer(modifier = Modifier.padding(bottom = 32.dp))
                            }
                        }
                    }
                }
            }

            ShizukuUtil.Error.SHIZUKU_NOT_RUNNNING -> {
                Text("Shizuku not running")
            }

            ShizukuUtil.Error.PERMISSION_DENIED -> {
                Text("Shizuku permission denied")
            }

            else -> {
                Text("Unknown error, check logcat")
            }
        }
    }
}

const val textLimit = 40

@Composable
fun ProcessItem(modifier: Modifier,proc: Proc,navController: NavController) {
        var name by remember { mutableStateOf("Loading") }
        var context = LocalContext.current
        var imageBitmap = remember { mutableStateOf<ImageBitmap?>(null) }
        var id = remember { mutableStateOf<Int>(com.rk.taskmanager.R.drawable.ic_android_black_24dp) }

        LaunchedEffect(Unit) {

            name = getApkNameFromPackage(context, proc.cmdLine).also {
                val icon = getAppIconBitmap(context, proc.cmdLine)
                if (icon != null){
                    imageBitmap.value = icon.asImageBitmap()
                }else{
                    imageBitmap.value = null
                }
            } ?: proc.name.also {
                if (proc.cmdLine.startsWith("/vendor") || proc.cmdLine.isEmpty()){
                    id.value = com.rk.taskmanager.R.drawable.cpu_24px
                }else if (proc.cmdLine.startsWith("/data/local/tmp") || proc.uid == 2000){
                    id.value = com.rk.taskmanager.R.drawable.usb_24px
                }
            }


        }

    val painter = painterResource(id = id.value)


    SettingsToggle(
        modifier,
        label = if (name.length > textLimit) {
            name.substring(0, textLimit) + "..."
        } else {
            name
        },
        description = if (proc.cmdLine.length > textLimit) {
            (proc.cmdLine.substring(0, textLimit) + "...").removePrefix("/system/bin/")
        } else {
            proc.cmdLine.removePrefix("/system/bin/")
        },
        showSwitch = false,
        default = false,
        sideEffect = {
            navController.navigate(SettingsRoutes.ProcessInfo.createRoute(proc.pid))
        },
        startWidget = {
            if (imageBitmap.value != null) {
                Image(
                    bitmap = imageBitmap.value!!,
                    contentDescription = "App Icon",
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Image(
                    painter = painter,
                    contentDescription = "Fallback Icon",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    )


}
