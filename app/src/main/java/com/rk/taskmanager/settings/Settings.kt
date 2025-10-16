package com.rk.taskmanager.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.rk.taskmanager.TaskManager

object Settings {
    var theme
        //0 is the id of the theme in the themes hashmap
        get() = Preference.getInt(key = "theme",0)
        set(value) = Preference.setInt(key = "theme",value)

    var updateFrequency
        //0 is the id of the theme in the themes hashmap
        get() = Preference.getInt(key = "updateFrequency",1000)
        set(value) = Preference.setInt(key = "updateFrequency",value)

    var workingMode
        get() = Preference.getInt(key = "workingMode", -1)
        set(value) = Preference.setInt(key = "workingMode",value)
    var monet
        get() = Preference.getBoolean(
            key = "monet",
            default = false
        )
        set(value) = Preference.setBoolean(key = "monet",value)

    var showSystemApps
        get() = Preference.getBoolean(
            key = "showSystemApps",
            default = true
        )
        set(value) = Preference.setBoolean(key = "showSystemApps",value)

    var showUserApps
        get() = Preference.getBoolean(
            key = "showUserApps",
            default = true
        )
        set(value) = Preference.setBoolean(key = "showUserApps",value)

    var showLinuxProcess
        get() = Preference.getBoolean(
            key = "showLinuxProcess",
            default = true
        )
        set(value) = Preference.setBoolean(key = "showLinuxProcess",value)
}

object Preference {
    private var sharedPreferences: SharedPreferences = TaskManager.requireContext().getSharedPreferences("Settings", Context.MODE_PRIVATE)

    //store the result into memory for faster access
    private val stringCache = hashMapOf<String, String?>()
    private val boolCache = hashMapOf<String, Boolean>()
    private val intCache = hashMapOf<String, Int>()
    private val longCache = hashMapOf<String, Long>()
    private val floatCache = hashMapOf<String, Float>()

    @SuppressLint("ApplySharedPref")
    fun clearData(){
        sharedPreferences.edit().clear().commit()
    }

    fun removeKey(key: String){
        if (sharedPreferences.contains(key).not()){
            return
        }

        sharedPreferences.edit().remove(key).apply()

        if (stringCache.containsKey(key)){
            stringCache.remove(key)
            return
        }

        if (boolCache.containsKey(key)){
            boolCache.remove(key)
            return
        }

        if (intCache.containsKey(key)){
            intCache.remove(key)
            return
        }

        if (longCache.containsKey(key)){
            longCache.remove(key)
            return
        }

        if (floatCache.containsKey(key)){
            floatCache.remove(key)
            return
        }
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        runCatching {
            return boolCache[key] ?: sharedPreferences.getBoolean(key, default)
                .also { boolCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setBoolean(key, default)
        }
        return default
    }

    fun setBoolean(key: String, value: Boolean) {
        boolCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putBoolean(key, value)
            editor.apply()
        }.onFailure { it.printStackTrace() }
    }



    fun getString(key: String, default: String): String {
        runCatching {
            return stringCache[key] ?: sharedPreferences.getString(key, default)!!
                .also { stringCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setString(key, default)
        }
        return default
    }
    fun setString(key: String, value: String?) {
        stringCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putString(key, value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }

    }

    fun getInt(key: String, default: Int): Int {
        runCatching {
            return intCache[key] ?: sharedPreferences.getInt(key, default)
                .also { intCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setInt(key, default)
        }
        return default
    }

    fun setInt(key: String, value: Int) {
        intCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putInt(key, value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }

    }

    fun getLong(key: String, default: Long): Long {
        runCatching {
            return longCache[key] ?: sharedPreferences.getLong(key, default)
                .also { longCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setLong(key, default)
        }
        return default
    }

    fun setLong(key: String, value: Long) {
        longCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putLong(key,value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }
    }

    fun getFloat(key: String, default: Float): Float {
        runCatching {
            return floatCache[key] ?: sharedPreferences.getFloat(key, default)
                .also { floatCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setFloat(key, default)
        }
        return default
    }

    fun setFloat(key: String, value: Float) {
        floatCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putFloat(key,value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }
    }

}