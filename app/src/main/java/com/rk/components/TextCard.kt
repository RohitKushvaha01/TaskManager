package com.rk.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rk.taskmanager.TaskManager

@Composable
fun TextCard(modifier: Modifier = Modifier,text: String,description: String? = null) {
    SettingsToggle(modifier = modifier, label = text, description = description, default = false, showSwitch = false, onLongClick = {
        val clipboard = TaskManager.requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(text, description)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(TaskManager.requireContext(), "Copied", Toast.LENGTH_SHORT).show()
    })
}
