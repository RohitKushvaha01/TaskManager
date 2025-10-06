package com.rk.taskmanager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import com.rk.taskmanager.ui.theme.TaskManagerTheme
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.rk.taskmanager.screens.CpuUsage
import com.rk.taskmanager.screens.MainScreen
import com.rk.taskmanager.screens.ProcessInfo
import com.rk.taskmanager.screens.SelectedWorkingMode
import com.rk.taskmanager.screens.SettingsScreen
import com.rk.taskmanager.screens.updateCpuGraph
import com.rk.taskmanager.screens.updateRamGraph
import com.rk.taskmanager.screens.updateSwapGraph
import com.rk.taskmanager.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference
import kotlin.math.max


class MainActivity : ComponentActivity() {

    val viewModel: ProcessViewModel by viewModels()

    companion object{
        var scope:CoroutineScope? = null
            private set
        var instance: MainActivity? = null
            private set
    }

    private var navControllerRef: WeakReference<NavController?> = WeakReference(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        scope = this.lifecycleScope
        instance = this

        val graphMutex = Mutex()

        lifecycleScope.launch(Dispatchers.IO) {
            daemon_messages.collect { message ->
                if (message.startsWith("CPU:") && graphMutex.tryLock()){
                    updateCpuGraph(message.removePrefix("CPU:").toInt())
                    delay(32)
                    graphMutex.unlock()
                }

                if (message.startsWith("SWAP:") && graphMutex.tryLock()){
                    updateRamGraph()

                    delay(32)

                    val parts = message.removePrefix("SWAP:").split(":")

                    if (parts.size == 2) {
                        val used = parts[0]
                        val total = parts[1]

                        val usedValue = used.toFloatOrNull() ?: 0f
                        val totalValue = total.toFloatOrNull() ?: 1f

                        val percentage = (usedValue / totalValue) * 100

                        updateSwapGraph(percentage.toInt(), usedValue.toLong(), totalValue.toLong())
                    }

                    graphMutex.unlock()
                }

                delay(16)
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive){
                graphMutex.withLock {
                    if (isConnected){
                        send_daemon_messages.emit("CPU_PING")
                        delay(16)
                        send_daemon_messages.emit("SWAP_PING")
                    }
                }
                delay(Settings.updateFrequency.toLong())
            }
        }

        setContent {
            TaskManagerTheme {
                Surface{
                    val navController = rememberNavController()
                    navControllerRef = WeakReference(navController)
                    NavHost(
                        navController = navController,
                        startDestination = if (Settings.workingMode == -1){
                            SettingsRoutes.SelectWorkingMode.route
                        }else{
                            SettingsRoutes.Home.route
                        },
                        enterTransition = { NavigationAnimationTransitions.enterTransition },
                        exitTransition = { NavigationAnimationTransitions.exitTransition },
                        popEnterTransition = { NavigationAnimationTransitions.popEnterTransition },
                        popExitTransition = { NavigationAnimationTransitions.popExitTransition },
                    ) {
                        composable(SettingsRoutes.Home.route){
                            MainScreen(navController = navController, viewModel = viewModel)
                        }

                        composable(SettingsRoutes.SelectWorkingMode.route){
                            SelectedWorkingMode(navController = navController)
                        }

                        composable(SettingsRoutes.Settings.route){
                            SettingsScreen(navController = navController)
                        }
                        composable("proc/{pid}"){
                            val pid = it.arguments?.getString("pid")!!.toInt()
                            ProcessInfo(pid = pid, navController = navController, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.workingMode != -1){
            lifecycleScope.launch(Dispatchers.Main) {
                val daemonResult = startDaemon(context = this@MainActivity, Settings.workingMode)
                if (daemonResult != DaemonResult.OK){
                    delay(3000)
                    if (isConnected.not()){
                        Toast.makeText(this@MainActivity, daemonResult.message ?: "Unable to start daemon!", Toast.LENGTH_SHORT).show()
                        if (navControllerRef.get()?.currentDestination?.route != SettingsRoutes.SelectWorkingMode.route){
                            navControllerRef.get()?.navigate(SettingsRoutes.SelectWorkingMode.route)
                        }

                    }
                }
            }
        }
        viewModel.refreshAuto()
    }
}

sealed class SettingsRoutes(val route: String){
    data object Home : SettingsRoutes("home")
    data object Settings : SettingsRoutes("settings")
    data object SelectWorkingMode : SettingsRoutes("SelectWorkingMode")
    data object ProcessInfo : SettingsRoutes("proc/{pid}"){
        fun createRoute(pid: Int) = "proc/$pid"
    }
}
