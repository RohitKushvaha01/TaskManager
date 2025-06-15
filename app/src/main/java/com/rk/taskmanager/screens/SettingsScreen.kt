package com.rk.taskmanager.screens

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.taskmanager.settings.Settings
import com.rk.taskmanager.ui.theme.currentTheme
import com.rk.taskmanager.ui.theme.dynamicTheme
import com.rk.taskmanager.ui.theme.themes
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    DelicateCoroutinesApi::class
)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier,navController: NavController) {
    PreferenceLayout(label = "Settings") {
        PreferenceGroup(heading = "Customization") {

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