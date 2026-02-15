package com.rk.taskmanager.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout

var pullToRefresh_procs by mutableStateOf(Settings.pullToRefresh_procs)

@Composable
fun ProcSettings(modifier: Modifier = Modifier) {
    PreferenceLayout(label = "Processes") {
        PreferenceGroup() {
            SettingsToggle(label = "Pull to refresh", description = "Allow pull to refresh in the processes screen", default = Settings.pullToRefresh_procs, showSwitch = true, sideEffect = {
                Settings.pullToRefresh_procs = it
                pullToRefresh_procs = it
            })
        }
    }
}