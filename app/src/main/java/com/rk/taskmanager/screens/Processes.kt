package com.rk.taskmanager.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
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
import com.rk.taskmanager.settings.pullToRefresh_procs
import com.rk.taskmanager.strings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Processes(
    modifier: Modifier = Modifier,
    viewModel: ProcessViewModel,
    navController: NavController
) {
    val scope = rememberCoroutineScope()

    if (showFilter.value) {
        XedDialog(onDismissRequest = { showFilter.value = false }) {

            val showUserApps by viewModel.showUserApps.collectAsState()
            val showSystemApps by viewModel.showSystemApps.collectAsState()
            val showLinuxProcess by viewModel.showLinuxProcess.collectAsState()

            DividerColumn {

                // USER APPS
                SettingsToggle(
                    label = stringResource(strings.show_user_app),
                    showSwitch = true,
                    default = showUserApps,
                    isEnabled = !(showUserApps && !showSystemApps && !showLinuxProcess),
                    sideEffect = { newValue ->
                        if (!newValue && !showSystemApps && !showLinuxProcess) return@SettingsToggle
                        scope.launch {
                            Settings.showUserApps = newValue
                            viewModel.setShowUserApps(newValue)
                        }
                    }
                )

                // SYSTEM APPS
                SettingsToggle(
                    label = stringResource(strings.show_system_app),
                    showSwitch = true,
                    default = showSystemApps,
                    isEnabled = !(showSystemApps && !showUserApps && !showLinuxProcess),
                    sideEffect = { newValue ->
                        if (!newValue && !showUserApps && !showLinuxProcess) return@SettingsToggle
                        scope.launch {
                            Settings.showSystemApps = newValue
                            viewModel.setShowSystemApps(newValue)
                        }
                    }
                )

                // LINUX PROCESS
                SettingsToggle(
                    label = stringResource(strings.show_linux_process),
                    showSwitch = true,
                    default = showLinuxProcess,
                    isEnabled = !(showLinuxProcess && !showUserApps && !showSystemApps),
                    sideEffect = { newValue ->
                        if (!newValue && !showUserApps && !showSystemApps) return@SettingsToggle
                        scope.launch {
                            Settings.showLinuxProcess = newValue
                            viewModel.setShowLinuxProcess(newValue)
                        }
                    }
                )
            }
        }

    }



    if (showSort.value) {
        XedDialog(onDismissRequest = { showSort.value = false }) {

            val sortBy by viewModel.sortBy.collectAsState()

            DividerColumn {
                SettingsToggle(default = false, showSwitch = false, startWidget = {
                    RadioButton(selected = sortBy == ProcessViewModel.Sortby.Ram.id, onClick = {
                        viewModel.setSortBy(ProcessViewModel.Sortby.Ram)
                    })
                }, label = "Sort by RAM", sideEffect = {
                    viewModel.setSortBy(ProcessViewModel.Sortby.Ram)
                })

                SettingsToggle(default = false, showSwitch = false, startWidget = {
                    RadioButton(selected = sortBy == ProcessViewModel.Sortby.Cpu.id, onClick = {
                        viewModel.setSortBy(ProcessViewModel.Sortby.Cpu)
                    })
                }, label = "Sort by CPU", sideEffect = {
                    viewModel.setSortBy(ProcessViewModel.Sortby.Cpu)
                })

                SettingsToggle(default = false, showSwitch = false, startWidget = {
                    RadioButton(selected = sortBy == ProcessViewModel.Sortby.A_z.id, onClick = {
                        viewModel.setSortBy(ProcessViewModel.Sortby.A_z)
                    })
                }, label = "Sort by Name (A-z)", sideEffect = {
                    viewModel.setSortBy(ProcessViewModel.Sortby.A_z)
                })



            }
        }

    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        val content = @Composable {
            val listState = rememberLazyListState()

            val filteredProcesses by viewModel.filteredProcesses.collectAsState()

            if (filteredProcesses.isNotEmpty()) {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState
                ) {

                    items(filteredProcesses, key = { it.proc.pid }) { uiProc ->
                        ProcessItem(modifier, uiProc, navController = navController, viewModel)
                    }

                    item {
                        Spacer(modifier = Modifier.padding(bottom = 32.dp))
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val messages = listOf(
                        "¯\\_(ツ)_/¯",
                        "(¬_¬ )",
                        "(╯°□°）╯︵ ┻━┻",
                        "(>_<)",
                        "(ಠ_ಠ)",
                        "(•_•) <(no data)",
                        "(o_O)"
                    )

                    val message = rememberSaveable { messages.random() }
                    Text(message)

                    Spacer(modifier = Modifier.padding(vertical = 16.dp))

                    Button(onClick = {
                        viewModel.refreshProcessesManual()
                    }) {
                        Text("Refresh")
                    }
                }

            }
        }

        if (pullToRefresh_procs){
            PullToRefreshBox(isRefreshing = viewModel.isLoading.value, onRefresh = {
                viewModel.refreshProcessesManual()
            }) {
                content()
            }
        }else{
            content()
        }
    }
}

const val textLimit = 40

@Composable
fun ProcessItem(
    modifier: Modifier,
    uiProc: ProcessUiModel,
    navController: NavController,
    viewModel: ProcessViewModel
) {

    PreferenceTemplate(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                indication = ripple(),
                enabled = !uiProc.killed.value,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { navController.navigate(SettingsRoutes.ProcessInfo.createRoute(uiProc)) }
            ),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        enabled = !uiProc.killed.value,
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
            //Text(uiProc.proc.cmdLine.removePrefix("/system/bin/").take(textLimit))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {

                // RAM Section
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.memory_alt_24px),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(2.dp))

                    Text(
                        text = "${uiProc.proc.memoryUsageKb / 1024} MB",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // CPU Section
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.cpu_24px),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(2.dp))

                    Text(
                        text = "${
                            String.format(Locale.ENGLISH, "%.1f", uiProc.proc.cpuUsage)
                        }%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

        },
        applyPaddings = false,
        startWidget = {
            if (uiProc.icon != null) {
                Image(
                    bitmap = uiProc.icon,
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .padding(start = 19.dp)
                        .size(24.dp),
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
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 19.dp)
                        .size(24.dp)
                        .alpha(if (!uiProc.killed.value) 1f else 0.3f),
                )
            }
        },
        endWidget = {
            if (uiProc.isUserApp) {
                if (uiProc.killing.value) {
                    CircularProgressIndicator(modifier = Modifier
                        .padding(end = 22.dp)
                        .size(16.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        modifier = Modifier.padding(end = 7.dp),
                        enabled = !uiProc.killed.value,
                        onClick = {
                            viewModel.viewModelScope.launch {
                                uiProc.killing.value = true
                                uiProc.killed.value = killProc(uiProc.proc)
                                delay(300)
                                uiProc.killing.value = false
                            }
                        }) {
                        if (uiProc.killed.value) {
                            Icon(imageVector = Icons.Outlined.Check, null)
                        } else {
                            Icon(imageVector = Icons.Outlined.Close, null)
                        }
                    }
                }

            }

        }

    )

}
