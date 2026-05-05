package com.rk.taskmanager.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.rk.commons.settings.Settings
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.taskmanager.MainActivity
import com.rk.commons.strings
import com.rk.taskmanager.ui.theme.currentTheme
import com.rk.taskmanager.ui.theme.dynamicTheme
import com.rk.taskmanager.ui.theme.themeMode
import com.rk.taskmanager.ui.theme.themes
import kotlinx.coroutines.launch

@Composable
fun Themes(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.themes)) {
        PreferenceGroup(heading = stringResource(strings.theme_mode)) {
            val modes = listOf(
                Triple(0, strings.auto, null),
                Triple(1, strings.light, null),
                Triple(2, strings.dark, null)
            )

            modes.forEach { (mode, labelRes, descRes) ->
                SelectableCard(
                    selected = themeMode.intValue == mode,
                    label = stringResource(labelRes),
                    description = if (descRes != null) stringResource(descRes) else null,
                    onClick = {
                        MainActivity.instance?.lifecycleScope?.launch {
                            themeMode.intValue = mode
                            Settings.themeMode = mode
                        }
                    }
                )
            }
        }


        PreferenceGroup(heading = stringResource(strings.theme)) {
            SelectableCard(
                selected = dynamicTheme.value,
                label = stringResource(strings.dynamic_theme),
                description = null,
                isEnaled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                onClick = {
                    MainActivity.instance?.lifecycleScope?.launch {
                        dynamicTheme.value = true
                        Settings.monet = true
                    }
                })

            themes.forEach {
                SelectableCard(
                    selected = currentTheme.intValue == it.key && !dynamicTheme.value,
                    label = stringResource(it.value.nameRes),
                    description = null,
                    onClick = {
                        MainActivity.instance?.lifecycleScope?.launch {
                            currentTheme.intValue = it.key
                            Settings.theme = it.key
                            if (dynamicTheme.value) {
                                dynamicTheme.value = false
                                Settings.monet = false
                            }
                        }
                    })
            }
        }
    }
}
