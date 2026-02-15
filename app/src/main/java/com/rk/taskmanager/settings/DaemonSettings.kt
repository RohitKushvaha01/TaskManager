package com.rk.taskmanager.settings

import android.widget.Toast
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.taskmanager.getString
import com.rk.taskmanager.strings

@Composable
fun DaemonSettings(modifier: Modifier = Modifier) {
    PreferenceLayout(label = "Daemon") {
        val context = LocalContext.current
        val selectedMode = remember { mutableIntStateOf(Settings.workingMode) }

        PreferenceGroup(heading = stringResource(strings.working_mode)) {
            WorkingMode.entries.forEach { mode ->
                SettingsToggle(
                    label = stringResource(mode.nameRes),
                    description = null,
                    default = selectedMode.intValue == mode.id,
                    sideEffect = {
                        Settings.workingMode = mode.id
                        selectedMode.intValue = mode.id

                        Toast.makeText(context, strings.requires_daemon_restart.getString(), Toast.LENGTH_SHORT).show()
                    },
                    showSwitch = false,
                    startWidget = {
                        RadioButton(selected = selectedMode.intValue == mode.id, onClick = {
                            Settings.workingMode = mode.id
                            selectedMode.intValue = mode.id
                            Toast.makeText(context, strings.requires_daemon_restart.getString(), Toast.LENGTH_SHORT)
                                .show()

                        })
                    },
                )
            }
        }
    }
}