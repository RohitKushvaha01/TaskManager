package com.rk.taskmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import com.rk.taskmanager.ui.theme.TaskManagerTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rk.taskmanager.animations.NavigationAnimationTransitions
import com.rk.taskmanager.screens.MainScreen
import com.rk.taskmanager.screens.ProcessInfo
import com.rk.taskmanager.screens.SettingsScreen
import kotlinx.coroutines.CoroutineScope


class MainActivity : ComponentActivity() {

    val viewModel: ProcessViewModel by viewModels()

    companion object{
        var scope:CoroutineScope? = null
            private set
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        scope = this.lifecycleScope

        setContent {
            TaskManagerTheme {
                Surface{
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = SettingsRoutes.Home.route,
                        enterTransition = { NavigationAnimationTransitions.enterTransition },
                        exitTransition = { NavigationAnimationTransitions.exitTransition },
                        popEnterTransition = { NavigationAnimationTransitions.popEnterTransition },
                        popExitTransition = { NavigationAnimationTransitions.popExitTransition },
                    ) {
                        composable(SettingsRoutes.Home.route){
                            MainScreen(navController = navController, viewModel = viewModel)
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
        viewModel.refreshAuto()
    }
}

sealed class SettingsRoutes(val route: String){
    data object Home : SettingsRoutes("home")
    data object Settings : SettingsRoutes("settings")
    data object ProcessInfo : SettingsRoutes("proc/{pid}"){
        fun createRoute(pid: Int) = "proc/$pid"
    }
}
