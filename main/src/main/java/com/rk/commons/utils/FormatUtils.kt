package com.rk.commons.utils

import java.util.Locale
import kotlin.math.roundToInt

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        val tb = gb * 1024

        return when {
            bytes >= tb.toLong() -> String.format(Locale.ENGLISH, "%.2f TB", bytes / tb)
            bytes >= gb.toLong() -> String.format(Locale.ENGLISH, "%.2f GB", bytes / gb)
            bytes >= mb.toLong() -> String.format(Locale.ENGLISH, "%.0f MB", bytes / mb)
            else -> String.format(Locale.ENGLISH, "%.0f KB", bytes / kb)
        }
    }
}

/** Converts a temperature in degrees Celsius to degrees Fahrenheit. */
fun celsiusToFahrenheit(celsius: Float): Float = celsius * 9f / 5f + 32f

/**
 * Formats a temperature using the metric (°C) or imperial (°F) scale,
 * depending on [imperial]. [celsius] is always expected to be in °C.
 */
fun formatTemperature(celsius: Float, imperial: Boolean, decimals: Int = 1): String {
    val value = if (imperial) celsiusToFahrenheit(celsius) else celsius
    val unit = if (imperial) "°F" else "°C"
    return String.format(Locale.ENGLISH, "%.${decimals}f $unit", value)
}

/** Integer overload of [formatTemperature], rounded to whole degrees. */
fun formatTemperature(celsius: Int, imperial: Boolean): String {
    val value = if (imperial) celsiusToFahrenheit(celsius.toFloat()).roundToInt() else celsius
    val unit = if (imperial) "°F" else "°C"
    return "$value $unit"
}
