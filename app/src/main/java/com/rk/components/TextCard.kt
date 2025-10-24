package com.rk.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.getString
import com.rk.taskmanager.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun TextCard(modifier: Modifier = Modifier,text: String?,description: String? = null,applyPaddings: Boolean = false,selection: Boolean = false,copyDesOnLong: Boolean = true) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    SettingsToggle(applyPaddingsNoSwitch = applyPaddings,
        modifier = modifier, label = text, description = description, default = false, showSwitch = false, selection = selection, onLongClick = {
            if (copyDesOnLong){
                val clipboard = TaskManager.requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(text, description)
                clipboard.setPrimaryClip(clip)

                scope.launch(Dispatchers.Main){
                    Toast.makeText(context, strings.copied.getString(), Toast.LENGTH_SHORT).show()
                }

            }
        })
}
