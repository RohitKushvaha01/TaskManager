package com.rk.commons.settings

import kotlin.reflect.KProperty

class BooleanPref(private val key: String? = null, private val default: Boolean) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = Preference.getBoolean(key ?: property.name, default)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) = Preference.setBoolean(key ?: property.name, value)
}

class IntPref(private val key: String? = null, private val default: Int) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = Preference.getInt(key ?: property.name, default)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) = Preference.setInt(key ?: property.name, value)
}

class StringPref(private val key: String? = null, private val default: String) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = Preference.getString(key ?: property.name, default)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) = Preference.setString(key ?: property.name, value)
}

class LongPref(private val key: String? = null, private val default: Long) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = Preference.getLong(key ?: property.name, default)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) = Preference.setLong(key ?: property.name, value)
}