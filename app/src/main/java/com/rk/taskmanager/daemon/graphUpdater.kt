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
import org.json.JSONObject

suspend fun CoroutineScope.graphUpdater(activity: MainActivity){
    val graphMutex = Mutex()
    launch(Dispatchers.Default) {
        daemon_messages.collect { message ->
            try {
                val json = JSONObject(message)
                val type = json.optString("type")
                graphMutex.withLock {
                    when (type) {
                        "CPU_USAGE" -> {
                            updateCpuGraph(json.optInt("usage"))
                        }
                        "GPU_USAGE" -> {
                            updateGpuGraph(json.optInt("usage"))
                        }
                        "SWAP_USAGE" -> {
                            val used = json.optLong("used", 0L)
                            val total = json.optLong("total", 1L)
                            val percentage = (used.toFloat() / total.toFloat()) * 100
                            updateRamAndSwapGraph(percentage.toInt(), used, total)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore non-graph messages
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
                    send_daemon_messages.emit(JSONObject().apply { put("cmd", "CPU_PING") }.toString())
                    delay(16)
                    send_daemon_messages.emit(JSONObject().apply { put("cmd", "SWAP_PING") }.toString())

                    if (hasSupportedGPU){
                        delay(15)
                        send_daemon_messages.emit(JSONObject().apply { put("cmd", "GPU_PING") }.toString())
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