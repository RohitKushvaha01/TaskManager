package com.rk.taskmanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import com.rk.commons.settings.Settings
import com.rk.taskmanager.ui.theme.cosmos.Cosmos
import com.rk.taskmanager.ui.theme.flame.Flame
import com.rk.taskmanager.ui.theme.leaf.Leaf
import com.rk.taskmanager.ui.theme.wave.Wave

abstract class Theme{
    abstract val nameRes:Int
    abstract val lightScheme: ColorScheme
    abstract val darkScheme: ColorScheme
}

val themes = hashMapOf(
    0 to Wave,
    1 to Leaf,
    2 to Flame,
    3 to Cosmos
)

var currentTheme = mutableIntStateOf(Settings.theme)
var dynamicTheme = mutableStateOf(Settings.monet)
var themeMode = mutableIntStateOf(Settings.themeMode)

@Composable
fun TaskManagerTheme(
    darkTheme: Boolean = when (themeMode.intValue) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    },
    dynamicColor: Boolean = dynamicTheme.value,
    content: @Composable () -> Unit
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }else{
        if (darkTheme){
            themes[currentTheme.intValue]!!.darkScheme
        }else{
            themes[currentTheme.intValue]!!.lightScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
