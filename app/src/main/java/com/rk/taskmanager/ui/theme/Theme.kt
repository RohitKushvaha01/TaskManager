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
import com.rk.taskmanager.settings.Settings
import com.rk.taskmanager.ui.theme.autumn.Autumn
import com.rk.taskmanager.ui.theme.frostfall.Frostfall
import com.rk.taskmanager.ui.theme.spring.Spring

abstract class Theme{
    abstract val lightScheme: ColorScheme
    abstract val darkScheme: ColorScheme
    abstract val mediumContrastLightColorScheme: ColorScheme
    abstract val highContrastLightColorScheme: ColorScheme
    abstract val mediumContrastDarkColorScheme: ColorScheme
    abstract val highContrastDarkColorScheme: ColorScheme
}

val themes = hashMapOf(0 to Autumn,1 to Frostfall,2 to Spring)
var currentTheme = mutableIntStateOf(Settings.theme)
var dynamicTheme = mutableStateOf(Settings.monet)

var amoledTheme = mutableStateOf(Settings.amoled)

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
            if (amoledTheme.value){
                themes[currentTheme.intValue]!!.highContrastDarkColorScheme
            }else{
                themes[currentTheme.intValue]!!.darkScheme
            }
        }else{
            if (amoledTheme.value){
                themes[currentTheme.intValue]!!.highContrastLightColorScheme
            }else{
                themes[currentTheme.intValue]!!.lightScheme
            }

        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}