package com.rk.taskmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.lifecycleScope
import com.rk.taskmanager.daemon.DaemonResult
import com.rk.taskmanager.daemon.graphUpdater
import com.rk.taskmanager.daemon.isConnected
import com.rk.taskmanager.daemon.startDaemon
import com.rk.taskmanager.screens.gpu.GpuViewModel
import com.rk.commons.Settings
import com.rk.taskmanager.settings.SettingsRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    val viewModel: ProcessViewModel by viewModels()
    val gpuViewModel: GpuViewModel by viewModels()

    companion object {
        var scope: CoroutineScope? = null
            private set
        var instance: MainActivity? = null
            private set
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        scope = this.lifecycleScope
        instance = this


        lifecycleScope.launch { graphUpdater(this@MainActivity) }


        setContent {
            RootContent()
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


