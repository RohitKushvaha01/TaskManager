package com.rk.taskmanager.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rk.commons.settings.BatteryCurrentUnit
import com.rk.commons.settings.Settings
import com.rk.commons.strings
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout

/**
 * Compose-observable mirror of [Settings.useImperialUnits] so that screens
 * showing measurements recompose as soon as the unit system changes.
 */
var useImperialUnits by mutableStateOf(Settings.useImperialUnits)

/**
 * Compose-observable mirror of [Settings.batteryCurrentUnit]. Some devices report
 * battery current in microamps while others report milliamps. The unit is detected
 * once at runtime and persisted, and can be overridden by the user.
 */
var batteryCurrentUnit by mutableStateOf(Settings.batteryCurrentUnit)

@Composable
fun Units(modifier: Modifier = Modifier) {
    PreferenceLayout(
        modifier = modifier,
        label = stringResource(strings.units),
    ) {
        PreferenceGroup(heading = stringResource(strings.unit_system)) {
            SelectableCard(
                selected = useImperialUnits.not(),
                label = stringResource(strings.metric),
                description = stringResource(strings.metric_desc),
                onClick = {
                    useImperialUnits = false
                    Settings.useImperialUnits = false
                }
            )

            SelectableCard(
                selected = useImperialUnits,
                label = stringResource(strings.imperial),
                description = stringResource(strings.imperial_desc),
                onClick = {
                    useImperialUnits = true
                    Settings.useImperialUnits = true
                }
            )
        }

        PreferenceGroup(heading = stringResource(strings.battery_current)) {
            SelectableCard(
                selected = batteryCurrentUnit != BatteryCurrentUnit.MILLIAMPS,
                label = stringResource(strings.battery_current_microamps),
                description = stringResource(strings.battery_current_microamps_desc),
                onClick = {
                    batteryCurrentUnit = BatteryCurrentUnit.MICROAMPS
                    Settings.batteryCurrentUnit = BatteryCurrentUnit.MICROAMPS
                }
            )

            SelectableCard(
                selected = batteryCurrentUnit == BatteryCurrentUnit.MILLIAMPS,
                label = stringResource(strings.battery_current_milliamps),
                description = stringResource(strings.battery_current_milliamps_desc),
                onClick = {
                    batteryCurrentUnit = BatteryCurrentUnit.MILLIAMPS
                    Settings.batteryCurrentUnit = BatteryCurrentUnit.MILLIAMPS
                }
            )
        }
    }
}
