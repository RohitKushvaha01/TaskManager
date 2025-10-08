package com.rk.taskmanager.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.DaemonResult
import com.rk.LoadingPopup
import com.rk.components.InfoBlock
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.startDaemon
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.settings.Settings
import com.rk.taskmanager.shizuku.ShizukuShell
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class WorkingMode(val id:Int){
    ROOT(0),
    SHIZUKU(1)
}

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun SelectedWorkingMode(modifier: Modifier = Modifier, navController: NavController) {
    val selectedMode = remember { mutableIntStateOf(Settings.workingMode) }
    var message by remember { mutableStateOf("") }
    val context = LocalContext.current

    PreferenceLayout(label = "WorkingMode") {
        Column(modifier = Modifier.fillMaxSize()) {

            InfoBlock(icon = {
                Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
            }, text = "TaskManager requires elevated permission to work, select the working mode you want to use.")

            Spacer(modifier = Modifier.padding(10.dp))

            LaunchedEffect(Unit) {
                if (!ShizukuShell.isPermissionGranted()) {
                    ShizukuShell.requestPermission()
                }
            }

            PreferenceGroup {
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
                                val daemonResult = startDaemon(context,mode.id)

                                withContext(Dispatchers.Main){
                                    when(daemonResult){
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

            if (message.isNotEmpty()) {
                Spacer(modifier = Modifier.padding(16.dp))
                Text(
                    text = message,
                    color = Color.Red,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
