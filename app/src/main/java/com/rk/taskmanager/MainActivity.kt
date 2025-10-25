package com.rk.taskmanager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.rk.DaemonResult
import com.rk.daemon_messages
import com.rk.isConnected
import com.rk.send_daemon_messages
import com.rk.startDaemon
import com.rk.taskmanager.ads.InterstitialsAds
import com.rk.taskmanager.ads.RewardedAds
import com.rk.taskmanager.animations.NavigationAnimationTransitions
import com.rk.taskmanager.screens.MainScreen
import com.rk.taskmanager.screens.ProcessInfo
import com.rk.taskmanager.screens.SelectedWorkingMode
import com.rk.taskmanager.screens.SettingsScreen
import com.rk.taskmanager.screens.procInfo
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
    var isinitialized = false
        private set

    var procInfo by mutableStateOf<ProcessUiModel?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        MobileAds.initialize(this@MainActivity)
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()

                //IF YOU ARE BUILDING THIS APP ADD YOUR DEVICE AS A TEST DEVICE
                .setTestDeviceIds(listOf("01AAD58E267D992B923B739EB497E211"))
                .build()
        )

        isinitialized = true
        if (Settings.shouldPreLoadThemeAd){
            RewardedAds.loadAd(this@MainActivity)
        }
        InterstitialsAds.loadAd(this@MainActivity){}




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
                    if (procInfo != null){
                        ProcessInfo(proc = procInfo!!, navController = navController, viewModel = viewModel)
                    }else{
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
}
