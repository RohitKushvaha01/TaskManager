package com.rk.taskmanager.daemon

import com.rk.taskmanager.MainActivity
import com.rk.taskmanager.navControllerRef
import com.rk.taskmanager.screens.cpu.updateCpuGraph
import com.rk.taskmanager.screens.gpu.updateGpuGraph
import com.rk.taskmanager.screens.ram.updateRamAndSwapGraph
import com.rk.taskmanager.screens.selectedscreen
import com.rk.taskmanager.settings.Settings
import com.rk.taskmanager.settings.SettingsRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

suspend fun CoroutineScope.graphUpdater(activity: MainActivity){
    val graphMutex = Mutex()
    launch(Dispatchers.Default) {
        daemon_messages.collect { message ->
            graphMutex.withLock {
                when {
                    message.startsWith("CPU:") -> {
                        updateCpuGraph(message.removePrefix("CPU:").toInt())
                    }
                    message.startsWith("GPU:") -> {
                        updateGpuGraph(message.removePrefix("GPU:").toInt())
                    }
                    message.startsWith("SWAP:") -> {
                        val parts = message.removePrefix("SWAP:").split(":")
                        if (parts.size == 2) {
                            val usedValue = parts[0].toFloatOrNull() ?: 0f
                            val totalValue = parts[1].toFloatOrNull() ?: 1f
                            val percentage = (usedValue / totalValue) * 100
                            updateRamAndSwapGraph(percentage.toInt(), usedValue.toLong(), totalValue.toLong())
                        }
                    }
                }
            }
        }
    }

    launch(Dispatchers.IO) {

        var hasSupportedGPU = false

        while (isActive) {
            if (isConnected) {
                if (!hasSupportedGPU) {
                    hasSupportedGPU = activity.gpuViewModel.gpuInfo.value?.renderer?.let { renderer ->
                        renderer.contains("mali", true) || renderer.contains("adreno", true)
                    } ?: false
                }

                graphMutex.withLock {
                    send_daemon_messages.emit("CPU_PING")
                    delay(16)
                    send_daemon_messages.emit("SWAP_PING")

                    if (hasSupportedGPU){
                        delay(15)
                        send_daemon_messages.emit("GPU_PING")
                    }
                }
            }
            val delayMs = if (selectedscreen.intValue == 0 && navControllerRef.get()?.currentDestination?.route == SettingsRoutes.Home.route) {
                Settings.updateFrequency.toLong()
            } else {
                Settings.updateFrequency.toLong() * 2
            }
            delay(delayMs)
        }
    }
}