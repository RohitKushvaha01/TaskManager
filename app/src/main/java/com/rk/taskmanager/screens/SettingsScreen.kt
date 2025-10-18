package com.rk.taskmanager.screens

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.taskmanager.MainActivity
import com.rk.taskmanager.ads.isAdAvailable
import com.rk.taskmanager.ads.loadAd
import com.rk.taskmanager.ads.showAd
import com.rk.taskmanager.getString
import com.rk.taskmanager.settings.Settings
import com.rk.taskmanager.settings.Settings.shouldPreLoadThemeAd
import com.rk.taskmanager.strings
import com.rk.taskmanager.ui.theme.currentTheme
import com.rk.taskmanager.ui.theme.dynamicTheme
import com.rk.taskmanager.ui.theme.themes
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    DelicateCoroutinesApi::class
)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, navController: NavController) {
    var showAdCallback by remember { mutableStateOf<(()->Unit)?>(null) }
    var showAdDialog by remember { mutableStateOf(false) }
    val activity = LocalActivity.current as MainActivity

    LaunchedEffect(Unit) {
        loadAd(activity!!)
    }

    PreferenceLayout(
        modifier = modifier,
        label = stringResource(strings.settings),
    ) {
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
                            Toast.makeText(context, strings.requires_daemon_restart.getString(), Toast.LENGTH_SHORT)
                                .show()

                        })
                    },
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
                    if (isAdAvailable()){
                        showAdCallback = {
                            showAd(activity = activity!!){
                                showAdDialog = false
                                Log.d("Settings","OK")
                                MainActivity.instance?.lifecycleScope?.launch {
                                    dynamicTheme.value = true
                                    Settings.monet = true
                                }
                                Settings.shouldPreLoadThemeAd = false

                            }
                        }
                        showAdDialog = true
                    }else{
                        MainActivity.instance?.lifecycleScope?.launch {
                            dynamicTheme.value = true
                            Settings.monet = true
                        }
                        loadAd(activity!!)
                    }
                })

            themes.forEach {
                SelectableCard(
                    selected = currentTheme.intValue == it.key && !dynamicTheme.value,
                    label = it.value::class.simpleName.toString(),
                    description = null,
                    onClick = {
                        if (isAdAvailable()){
                            showAdCallback= {
                                showAd(activity = activity!!){
                                    showAdDialog = false
                                    Log.d("Settings","OK")
                                    MainActivity.instance?.lifecycleScope?.launch {
                                        currentTheme.intValue = it.key
                                        Settings.theme = it.key
                                        if (dynamicTheme.value) {
                                            dynamicTheme.value = false
                                            Settings.monet = false
                                        }
                                    }
                                    Settings.shouldPreLoadThemeAd = false

                                }
                            }
                            showAdDialog = true
                        }else{
                            MainActivity.instance?.lifecycleScope?.launch {
                                currentTheme.intValue = it.key
                                Settings.theme = it.key
                                if (dynamicTheme.value) {
                                    dynamicTheme.value = false
                                    Settings.monet = false
                                }
                            }
                            loadAd(activity!!)
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

        if (showAdDialog){
            RewardAdDialog(onDismiss = { showAdDialog = false }, onWatchAd = {
                showAdDialog = false
                showAdCallback?.invoke()
            })
        }

    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableCard(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    description: String? = null,
    onClick: () -> Unit,
    isEnaled: Boolean = true
) {
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
            RadioButton(selected = selected, onClick = onClick, modifier = Modifier.padding(start = 8.dp))
        }
    )
}


@Composable
fun RewardAdDialog(
    onDismiss: () -> Unit,
    onWatchAd: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.ad)) },
        text = {
            Text(stringResource(strings.ad_desc))
        },
        confirmButton = {
            TextButton(onClick = onWatchAd) {
                Text(stringResource(strings.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(strings.cancel))
            }
        }
    )
}