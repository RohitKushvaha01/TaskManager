package com.rk.taskmanager.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rk.commons.settings.Settings
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.commons.strings

var pullToRefresh_procs by mutableStateOf(Settings.pullToRefresh_procs)

@Composable
fun ProcSettings(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.procs)) {
        PreferenceGroup() {
            SettingsToggle(label = stringResource(strings.pull_to_refresh), description = stringResource(strings.pull_to_refresh_desc), default = Settings.pullToRefresh_procs, showSwitch = true, sideEffect = {
                Settings.pullToRefresh_procs = it
                pullToRefresh_procs = it
            })
            SettingsToggle(label = stringResource(strings.confirm_stop), description = stringResource(strings.confirm_stop_desc), default = Settings.confirmkill, showSwitch = true, sideEffect = {
                Settings.confirmkill = it
            })
            SettingsToggle(label = stringResource(strings.default_to_process), description = stringResource(strings.default_to_process_desc), default = Settings.defaultToProcessScreen, showSwitch = true, sideEffect = {
                Settings.defaultToProcessScreen = it
            })
            SettingsToggle(label = stringResource(strings.auto_refresh), description = stringResource(strings.auto_refresh_desc), default = Settings.procAutoRefresh
                , showSwitch = true, sideEffect = {
                Settings.procAutoRefresh = it
            })
        }
    }
}