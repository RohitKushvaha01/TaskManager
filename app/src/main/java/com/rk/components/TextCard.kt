package com.rk.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TextCard(modifier: Modifier = Modifier,text: String,description: String? = null) {
    SettingsToggle(label = text, description = description, default = false, showSwitch = false)
}