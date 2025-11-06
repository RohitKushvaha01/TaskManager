package com.rk.taskmanager.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.DaemonResult
import com.rk.components.InfoBlock
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.isSuInPath
import com.rk.startDaemon
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.settings.Settings
import com.rk.taskmanager.shizuku.ShizukuShell
import com.rk.taskmanager.strings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WorkingMode(val id:Int,val nameRes: Int){
    ROOT(0,strings.root),
    SHIZUKU(1,strings.shizuku)
}

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun SelectedWorkingMode(modifier: Modifier = Modifier, navController: NavController) {
    val selectedMode = remember { mutableIntStateOf(Settings.workingMode) }
    var message by remember { mutableStateOf("") }
    val context = LocalContext.current
    var isNoob by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isNoob = isSuInPath().not() && ShizukuShell.isShizukuInstalled().not()
    }

    PreferenceLayout(
        modifier = modifier,
        label = stringResource(strings.working_mode)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            InfoBlock(icon = {
                Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
            }, text = stringResource(strings.intro))

            Spacer(modifier = Modifier.padding(10.dp))

            LaunchedEffect(Unit) {
                if (!ShizukuShell.isPermissionGranted()) {
                    ShizukuShell.requestPermission()
                }
            }

            PreferenceGroup(heading = stringResource(strings.working_mode)) {
                WorkingMode.entries.forEach { mode ->
                    SettingsToggle(
                        label = mode.name,
                        description = null,
                        default = selectedMode.intValue == mode.id,
                        sideEffect = {
                            Settings.workingMode = mode.id
                            selectedMode.intValue = mode.id
                            message = ""

                            GlobalScope.launch(Dispatchers.IO) {
                                val daemonResult = startDaemon(context, mode.id)

                                withContext(Dispatchers.Main) {
                                    when (daemonResult) {
                                        DaemonResult.OK -> {
                                            navController.navigate(SettingsRoutes.Home.route)
                                        }

                                        else -> {
                                            message = daemonResult.message.toString()
                                        }
                                    }
                                }

                            }
                        },
                        showSwitch = false,
                        startWidget = {},
                        endWidget = {
                            Icon(
                                modifier = Modifier.padding(end = 10.dp),
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            if (isNoob) {
                message = stringResource(strings.noob)
            }

            if (message.isNotEmpty()) {
                Spacer(modifier = Modifier.padding(16.dp))
                PreferenceGroup(heading = stringResource(strings.info)) {
                    SettingsToggle(description = message, default = false, showSwitch = false)
                }
            }
        }
    }
}
