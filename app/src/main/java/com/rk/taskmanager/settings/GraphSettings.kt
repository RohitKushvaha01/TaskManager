package com.rk.taskmanager.settings

import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.taskmanager.strings

@Composable
fun GraphSettings(modifier: Modifier = Modifier) {
    PreferenceLayout(label = "Graph"){
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
                Text(stringResource(strings.graph_update))
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