package com.rk.taskmanager.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.startDaemon
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.getString
import com.rk.taskmanager.settings.Settings
import com.rk.taskmanager.strings
import com.rk.taskmanager.ui.theme.currentTheme
import com.rk.taskmanager.ui.theme.dynamicTheme
import com.rk.taskmanager.ui.theme.themes
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    DelicateCoroutinesApi::class
)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier,navController: NavController) {
    PreferenceLayout(label = stringResource(strings.settings),) {
        val context = LocalContext.current
        val selectedMode = remember { mutableIntStateOf(Settings.workingMode) }

        PreferenceGroup(heading = stringResource(strings.working_mode)) {
            WorkingMode.entries.forEach { mode ->
                SettingsToggle(
                    label = mode.name,
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
                            Toast.makeText(context, strings.requires_daemon_restart.getString(), Toast.LENGTH_SHORT).show()

                        })
                    },
                )
            }
        }

        PreferenceGroup(heading = "Theme") {

            SelectableCard(selected = dynamicTheme.value, label = "Dynamic Theme", description = null, isEnaled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S, onClick = {
                GlobalScope.launch{
                    dynamicTheme.value = true
                    Settings.monet = true
                }
            })

            themes.forEach{
                SelectableCard(selected = currentTheme.intValue == it.key && !dynamicTheme.value, label = it.value::class.simpleName.toString(), description = null, onClick = {
                    GlobalScope.launch{
                        currentTheme.intValue = it.key
                        Settings.theme = it.key
                        if (dynamicTheme.value){
                            dynamicTheme.value = false
                            Settings.monet = false
                        }
                    }
                })
            }
        }


        val minFreq = 150 // 150ms at 0%
        val maxFreq = 1000

        var sliderPosition by rememberSaveable {
            mutableFloatStateOf(
                ((Settings.updateFrequency - minFreq).toFloat() / (maxFreq - minFreq))
                    .coerceIn(0f, 1f)
            )
        }

        PreferenceGroup {
            PreferenceTemplate(title = {
                Text("Graph update frequency")
            }) {
                val currentFreq = (minFreq + (sliderPosition * (maxFreq - minFreq))).toInt()
                Text("${currentFreq}ms")
            }
            PreferenceTemplate(title = {}) {
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = {
                        Settings.updateFrequency = (minFreq + (sliderPosition * (maxFreq - minFreq))).toInt()
                    }
                )
            }
        }




    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableCard(modifier: Modifier = Modifier, selected: Boolean, label: String, description: String? = null, onClick:()-> Unit,isEnaled: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    PreferenceTemplate(
        modifier = modifier.combinedClickable(
            enabled = isEnaled,
            indication = ripple(),
            interactionSource = interactionSource,
            onClick = onClick
        ),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = { Text(fontWeight = FontWeight.Bold, text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        description = { description?.let { Text(it) } },
        enabled = isEnaled,
        applyPaddings = false,
        endWidget = null,
        startWidget = {
            RadioButton(selected = selected, onClick = onClick,modifier = Modifier.padding(start = 8.dp))
        }
    )
}