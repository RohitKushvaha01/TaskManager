package com.rk.commons.settings

object BatteryCurrentUnit {
    const val UNKNOWN = -1
    const val MICROAMPS = 0
    const val MILLIAMPS = 1
}

object Settings {
    var procAutoRefresh by BooleanPref(key = "proc_auto_refresh", default = true)
    var theme by IntPref(default = 0)
    var themeMode by IntPref(key = "theme_mode", default = 0)
    var sortby by IntPref(default = 0)
    var updateFrequency by IntPref(default = 800)
    var workingMode by IntPref(default = -1)
    var monet by BooleanPref(default = false)
    var showSystemApps by BooleanPref(default = true)
    var showUserApps by BooleanPref(default = true)
    var showLinuxProcess by BooleanPref(default = false)
    var kills by IntPref(default = 0)
    var pullToRefresh_procs by BooleanPref(default = true)
    var confirmkill by BooleanPref(default = true)
    var defaultToProcessScreen by BooleanPref(default = false)
    var selectedNetInterface by StringPref(key = "selected_net_interface", default = "")
    var useImperialUnits by BooleanPref(key = "use_imperial_units", default = false)
    var batteryCurrentUnit by IntPref(key = "battery_current_unit", default = BatteryCurrentUnit.UNKNOWN)

    var pinnedProcesses: Set<String>
        get() = Preference.getString("pinned_processes", "").split(",").filter { it.isNotEmpty() }.toSet()
        set(value) = Preference.setString("pinned_processes", value.joinToString(","))
}