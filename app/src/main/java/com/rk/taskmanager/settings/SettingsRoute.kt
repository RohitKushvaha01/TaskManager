package com.rk.taskmanager.settings

import com.rk.taskmanager.ProcessUiModel
import com.rk.taskmanager.screens.procByPid
import java.lang.ref.WeakReference
import kotlin.collections.set

sealed class SettingsRoutes(val route: String) {
    //main
    data object Home : SettingsRoutes("home")
    data object Settings : SettingsRoutes("settings")
    data object SelectWorkingMode : SettingsRoutes("SelectWorkingMode")
    data object ProcessInfo : SettingsRoutes("proc/{pid}") {
        fun createRoute(proc: ProcessUiModel):String{
            procByPid[proc.proc.pid] = WeakReference(proc)
            return "proc/${proc.proc.pid}"
        }
    }

    //settings


    data object Daemon : SettingsRoutes("daemon")
    data object Graphs : SettingsRoutes("graphs")
    data object Procs : SettingsRoutes("procs")
    data object Themes : SettingsRoutes("themes")
    data object About : SettingsRoutes("about")

}