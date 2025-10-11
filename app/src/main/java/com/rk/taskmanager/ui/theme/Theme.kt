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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.rk.taskmanager.settings.Settings
import com.rk.taskmanager.ui.theme.autumn.Autumn
import com.rk.taskmanager.ui.theme.frostfall.Frostfall
import com.rk.taskmanager.ui.theme.spring.Spring

abstract class Theme{
    abstract val lightScheme: ColorScheme
    abstract val darkScheme: ColorScheme
}

val themes = hashMapOf(
    0 to Frostfall,
    1 to Autumn,
    2 to Spring
)

var currentTheme = mutableIntStateOf(Settings.theme)
var dynamicTheme = mutableStateOf(Settings.monet)

@Composable
fun TaskManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
