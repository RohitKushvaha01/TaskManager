package com.rk.taskmanager

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.taskmanager.screens.getApkNameFromPackage
import com.rk.taskmanager.screens.getAppIconBitmap
import com.rk.taskmanager.screens.isAppInstalled
import com.rk.taskmanager.screens.isSystemApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.rk.daemon_messages
import com.rk.send_daemon_messages
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.json.JSONArray

data class ProcessUiModel(
    val proc: ProcessViewModel.Process,
    val name: String,
    val icon: ImageBitmap?,
    val isSystemApp: Boolean,
    val isInstalledApp: Boolean
)

class ProcessViewModel : ViewModel() {

    var processes = mutableStateListOf<Process>()
        private set

    private val _uiProcesses = MutableStateFlow<List<ProcessUiModel>>(emptyList())
    val uiProcesses: StateFlow<List<ProcessUiModel>> = _uiProcesses

    var isLoading = mutableStateOf(true)

    data class Process(
        val name: String,
        var nice: Int,
        val pid: Int,
        val uid: Int,
        val cpuUsage: Float,
        val parentPid: Int,
        val isForeground: Boolean,
        val memoryUsageKb: Long,
        val cmdLine: String,
        val state: String,
        val threads: Int,
        val startTime: Long,
        val elapsedTime: Float,
        val residentSetSizeKb: Long,
        val virtualMemoryKb: Long,
        val cgroup: String,
        val executablePath: String
    )

    init {
        viewModelScope.launch {
            daemon_messages.collect { message ->
                if (message.startsWith("[") && message.endsWith("]")) {
                    try {
                        val jsonArray = JSONArray(message)
                        val newProcesses = mutableListOf<Process>()

                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            newProcesses.add(
                                Process(
                                    name = obj.optString("name", ""),
                                    nice = obj.optInt("nice", 0),
                                    pid = obj.optInt("pid", 0),
                                    uid = obj.optInt("uid", 0),
                                    cpuUsage = obj.optDouble("cpuUsage", 0.0).toFloat(),
                                    parentPid = obj.optInt("parentPid", 0),
                                    isForeground = obj.optBoolean("isForeground", false),
                                    memoryUsageKb = obj.optLong("memoryUsageKb", 0L),
                                    cmdLine = obj.optString("cmdLine", ""),
                                    state = obj.optString("state", ""),
                                    threads = obj.optInt("threads", 0),
                                    startTime = obj.optLong("startTime", 0L),
                                    elapsedTime = obj.optDouble("elapsedTime", 0.0).toFloat(),
                                    residentSetSizeKb = obj.optLong("residentSetSizeKb", 0L),
                                    virtualMemoryKb = obj.optLong("virtualMemoryKb", 0L),
                                    cgroup = obj.optString("cgroup", ""),
                                    executablePath = obj.optString("executablePath", "")
                                )
                            )
                        }

                        Log.d("ProcessList", "Received ${processes.size} processes")
                        val uiList = newProcesses.map { proc ->
                            async {
                                val context = TaskManager.getContext()
                                val name = getApkNameFromPackage(context, proc.cmdLine) ?: proc.name
                                val icon = getAppIconBitmap(context, proc.cmdLine)?.asImageBitmap()
                                val system = isSystemApp(context, proc.cmdLine)
                                val installed = isAppInstalled(context, proc.cmdLine)
                                ProcessUiModel(proc, name, icon, system, installed)
                            }
                        }.awaitAll()


                        withContext(Dispatchers.Main) {
                            processes.clear()
                            processes.addAll(newProcesses)
                            _uiProcesses.value = uiList
                            isLoading.value = false
                        }

                    } catch (e: Exception) {
                        Log.e("ProcessList", "Failed to parse process list: ${e.message}")
                    }
                }
            }
        }

        viewModelScope.launch {
            refreshProcesses()
        }

    }


    fun refreshProcesses() {
        isLoading.value = true
        viewModelScope.launch {
            send_daemon_messages.emit("LIST_PROCESS")
        }
    }

    fun refreshAuto() {
        if (processes.isEmpty()) {
            refreshProcesses()
        }
    }
}
