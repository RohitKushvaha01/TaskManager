package com.rk.taskmanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import com.rk.taskmanager.ui.theme.spring.Spring

abstract class Theme{
    abstract val lightScheme: ColorScheme
    abstract val darkScheme: ColorScheme
    abstract val mediumContrastLightColorScheme: ColorScheme
    abstract val highContrastLightColorScheme: ColorScheme
    abstract val mediumContrastDarkColorScheme: ColorScheme
    abstract val highContrastDarkColorScheme: ColorScheme
}


var theme = mutableStateOf<Theme>(Spring)

@Composable
fun TaskManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {

    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }else{
        if (darkTheme){
            theme.value.darkScheme
        }else{
            theme.value.lightScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}