package com.rk.taskmanager.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.taskmanager.R

var pullToRefresh_procs by mutableStateOf(Settings.pullToRefresh_procs)

@Composable
fun ProcSettings(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(R.string.processes)) {
        PreferenceGroup() {
            SettingsToggle(label = stringResource(R.string.pull_to_refresh), description = stringResource(
                R.string.allow_pull_to_refresh_in_the_processes_screen
            ), default = Settings.pullToRefresh_procs, showSwitch = true, sideEffect = {
                Settings.pullToRefresh_procs = it
                pullToRefresh_procs = it
            })
            SettingsToggle(label = stringResource(R.string.confirm_stop), description = stringResource(
                R.string.confirm_before_killing_a_process
            ), default = Settings.confirmkill, showSwitch = true, sideEffect = {
                Settings.confirmkill = it
            })
        }
    }
}