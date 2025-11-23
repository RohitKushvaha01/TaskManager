package com.rk.taskmanager

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rk.DaemonResult
import com.rk.daemon_messages
import com.rk.isConnected
import com.rk.send_daemon_messages
import com.rk.startDaemon
import com.rk.taskmanager.animations.NavigationAnimationTransitions
import com.rk.taskmanager.screens.MainScreen
import com.rk.taskmanager.screens.ProcessInfo
import com.rk.taskmanager.screens.SelectedWorkingMode
import com.rk.taskmanager.screens.SettingsScreen
import com.rk.taskmanager.screens.procByPid
import com.rk.taskmanager.screens.selectedscreen
import com.rk.taskmanager.screens.updateCpuGraph
import com.rk.taskmanager.screens.updateRamAndSwapGraph
import com.rk.taskmanager.settings.Settings
import com.rk.taskmanager.ui.theme.TaskManagerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference


class MainActivity : ComponentActivity() {

    val viewModel: ProcessViewModel by viewModels()

    companion object {

        var scope: CoroutineScope? = null
            private set
        var instance: MainActivity? = null
            private set
    }

    var navControllerRef: WeakReference<NavController?> = WeakReference(null)
        private set

     private val TAG = "MainActivity"

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        scope = this.lifecycleScope
        instance = this

        val graphMutex = Mutex()

        lifecycleScope.launch(Dispatchers.IO) {
            daemon_messages.collect { message ->
                launch {
                    graphMutex.lock()
                    if (message.startsWith("CPU:")) {
                        updateCpuGraph(message.removePrefix("CPU:").toInt())
                        delay(32)
                    }

                    if (message.startsWith("SWAP:")) {
                        val parts = message.removePrefix("SWAP:").split(":")
                        if (parts.size == 2) {
                            val used = parts[0]
                            val total = parts[1]

                            val usedValue = used.toFloatOrNull() ?: 0f
                            val totalValue = total.toFloatOrNull() ?: 1f

                            val percentage = (usedValue / totalValue) * 100

                            updateRamAndSwapGraph(percentage.toInt(), usedValue.toLong(), totalValue.toLong())
                        }
                    }

                    graphMutex.unlock()
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                graphMutex.withLock {
                    if (isConnected) {
                        send_daemon_messages.emit("CPU_PING")
                        delay(16)
                        send_daemon_messages.emit("SWAP_PING")
                    }
                }
                val delayMs = if (selectedscreen.intValue == 0 && MainActivity.instance?.navControllerRef?.get()?.currentDestination?.route == SettingsRoutes.Home.route) {
                    Settings.updateFrequency.toLong()
                } else {
                    Settings.updateFrequency.toLong() * 2
                }
                delay(delayMs)
            }
        }

        setContent {
            TaskManagerTheme {
                Surface {
                    val navController = rememberNavController()
                    navControllerRef = WeakReference(navController)
                    NavHost(
                        navController = navController,
                        startDestination = if (Settings.workingMode == -1) {
                            SettingsRoutes.SelectWorkingMode.route
                        } else {
                            SettingsRoutes.Home.route
                        },
                        enterTransition = { NavigationAnimationTransitions.enterTransition },
                        exitTransition = { NavigationAnimationTransitions.exitTransition },
                        popEnterTransition = { NavigationAnimationTransitions.popEnterTransition },
                        popExitTransition = { NavigationAnimationTransitions.popExitTransition },
                    ) {
                        composable(SettingsRoutes.Home.route) {
                            MainScreen(navController = navController, viewModel = viewModel)
                        }

                        composable(SettingsRoutes.SelectWorkingMode.route) {
                            SelectedWorkingMode(navController = navController)
                        }

                        composable(SettingsRoutes.Settings.route) {
                            SettingsScreen(navController = navController)
                        }
                        composable("proc/{pid}") {
                            val pid = it.arguments?.getString("pid")!!.toInt()
                            val proc = remember { procByPid[pid]?.get() }

                            if (proc != null) {
                                LaunchedEffect(Unit) {
                                    if (proc.killed.value){
                                        navController.popBackStack()
                                        return@LaunchedEffect
                                    }
                                }
                                ProcessInfo(proc = proc, navController = navController, viewModel = viewModel)
                            }else{
                                navController.popBackStack()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.workingMode != -1) {
            lifecycleScope.launch(Dispatchers.Main) {
                val daemonResult = startDaemon(context = this@MainActivity, Settings.workingMode)
                if (daemonResult != DaemonResult.OK) {
                    delay(3000)

                    if (isConnected.not()){
                        if (navControllerRef.get()?.currentDestination?.route != SettingsRoutes.SelectWorkingMode.route){
                            navControllerRef.get()?.navigate(SettingsRoutes.SelectWorkingMode.route)
                        }

                    }
                }
            }
        }
        viewModel.refreshProcessesAuto()
    }
}

sealed class SettingsRoutes(val route: String) {
    data object Home : SettingsRoutes("home")
    data object Settings : SettingsRoutes("settings")
    data object SelectWorkingMode : SettingsRoutes("SelectWorkingMode")
    data object ProcessInfo : SettingsRoutes("proc/{pid}") {
        fun createRoute(proc: ProcessUiModel):String{
            procByPid[proc.proc.pid] = WeakReference(proc)
            return "proc/${proc.proc.pid}"
        }
    }
}
