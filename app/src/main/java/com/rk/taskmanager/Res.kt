@file:Suppress("NOTHING_TO_INLINE")

package com.rk.taskmanager

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

typealias drawables = R.drawable
typealias strings = R.string

fun Int.getString(values: Map<String, Any>? = null): String {
    val string = ContextCompat.getString(TaskManager.requireContext(), this)
    return if (values != null) string.fillPlaceholders(values) else string
}

inline fun Int.getDrawable(context: Context): Drawable? {
    return ContextCompat.getDrawable(context, this)
}

inline fun Int.getFilledString(values: Map<String, Any>): String {
    return this.getString().fillPlaceholders(values)
}

inline fun String.fillPlaceholders(values: Map<String, Any>): String {
    var result = this

    values.forEach { (key, value) ->
        result = result.replace("%($key)", value.toString())
    }

    return result
}
