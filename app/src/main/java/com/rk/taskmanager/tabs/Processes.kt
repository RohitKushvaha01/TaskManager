package com.rk.taskmanager.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.shizuku.Proc
import com.rk.taskmanager.shizuku.ShizukuUtil


@Composable
fun Processes(
    modifier: Modifier = Modifier,
    viewModel: ProcessViewModel,
    navController: NavController
) {
    val state by viewModel.state.collectAsState()
    val processes = viewModel.processes
    val isLoading = viewModel.isLoading.value
    val listState = rememberLazyListState()


    LaunchedEffect(Unit) {
        viewModel.refreshAuto()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            ShizukuUtil.Error.NO_ERROR -> {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState
                    ) {
                        items(processes.size, key = { processes[it].pid }) { index ->
                            ProcessItem(processes[index],navController = navController)
                        }

                        item(key = null) {
                            Spacer(modifier = Modifier.padding(bottom = 32.dp))
                        }
                    }

                }
            }

            ShizukuUtil.Error.SHIZUKU_NOT_RUNNNING -> {
                Text("Shizuku Server not running")
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
fun ProcessItem(proc: Proc,navController: NavController) {
    PreferenceGroup {
        SettingsToggle(
            label = if (proc.name.length > textLimit) {
                proc.name.substring(0, textLimit) + "..."
            } else {
                proc.name
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
            }
        )
    }
}
