package com.rk.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.switch.PreferenceSwitch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsToggle(
    modifier: Modifier = Modifier,
    label: String? = null,
    description: String? = null,
    @DrawableRes iconRes: Int? = null,
    default: Boolean,
    reactiveSideEffect: ((checked: Boolean) -> Boolean)? = null,
    sideEffect: ((checked: Boolean) -> Unit)? = null,
    showSwitch: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
    isSwitchLocked: Boolean = false,
    applyPaddingsNoSwitch: Boolean = false,
    selection: Boolean = false,
    startWidget: (@Composable () -> Unit)? = null,
    endWidget: (@Composable () -> Unit)? = null,
) {
    if (showSwitch && endWidget != null){
        throw IllegalStateException("endWidget with show switch")
    }

    if (showSwitch) {
        var state by remember {
            mutableStateOf(default)
        }
        PreferenceSwitch(checked = state,
            onLongClick = onLongClick,
            onCheckedChange = {
                if (isSwitchLocked.not()) {
                    state = !state
                }
                if (reactiveSideEffect != null) {
                    state = reactiveSideEffect.invoke(state) == true
                } else {
                    sideEffect?.invoke(state)
                }

            },
            label = label,
            modifier = modifier,
            description = description,
            enabled = isEnabled,
            onClick = {
                if (isSwitchLocked.not()) {
                    state = !state
                }
                if (reactiveSideEffect != null) {
                    state = reactiveSideEffect.invoke(state) == true
                } else {
                    sideEffect?.invoke(state)
                }
            })
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        PreferenceTemplate(
            modifier = modifier.combinedClickable(
                enabled = isEnabled,
                indication = ripple(),
                interactionSource = interactionSource,
                onLongClick = onLongClick,
                onClick = { sideEffect?.invoke(false) }
            ),
            contentModifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp)
                .padding(start = 16.dp),
            title = {
                if (label != null) {
                   if (selection){
                       SelectionContainer {
                           Text(fontWeight = FontWeight.Bold, text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                       }
                   }else{
                       Text(fontWeight = FontWeight.Bold, text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                   }
                }
            },
            description = { description?.let {
                if (selection){
                    SelectionContainer {
                        Text(text = it)
                    }
                }else{
                    Text(it)
                }
            } },
            enabled = true,
            applyPaddings = applyPaddingsNoSwitch,
            endWidget = endWidget,
            startWidget = startWidget
        )
    }


}