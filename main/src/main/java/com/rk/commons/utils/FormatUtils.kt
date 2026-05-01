package com.rk.commons.utils

import java.util.Locale

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
