package com.rk.taskmanager

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.daemon_messages
import com.rk.send_daemon_messages
import com.rk.taskmanager.screens.getApkNameFromPackage
import com.rk.taskmanager.screens.getAppIconBitmap
import com.rk.taskmanager.screens.isAppInstalled
import com.rk.taskmanager.screens.isSystemApp
import com.rk.taskmanager.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class ProcessUiModel(
    val proc: ProcessViewModel.Process,
    val name: String,
    val icon: ImageBitmap?,
    val isSystemApp: Boolean,
    val isUserApp: Boolean,
    val isApp: Boolean,
    val killing: MutableState<Boolean> = mutableStateOf(false),
    val killed: MutableState<Boolean> = mutableStateOf(false)
)

@OptIn(FlowPreview::class)
class ProcessViewModel : ViewModel() {
    private val _uiProcesses = MutableStateFlow<List<ProcessUiModel>>(emptyList())

    private val _showUserApps = MutableStateFlow(Settings.showUserApps)
    private val _showSystemApps = MutableStateFlow(Settings.showSystemApps)
    private val _showLinuxProcess = MutableStateFlow(Settings.showLinuxProcess)

    val showUserApps = _showUserApps.asStateFlow()
    val showSystemApps = _showSystemApps.asStateFlow()
    val showLinuxProcess = _showLinuxProcess.asStateFlow()

    val filteredProcesses: StateFlow<List<ProcessUiModel>> = combine(
        _uiProcesses,
        _showUserApps,
        _showSystemApps,
        _showLinuxProcess
    ) { processes, showUser, showSystem, showLinux ->
        processes.filter { process ->
            when {
                process.isApp && process.isUserApp && showUser -> true
                process.isApp && process.isSystemApp && showSystem -> true
                process.isApp.not() && showLinux -> true
                else -> false
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Use StateFlow for search query with debouncing
    private val _searchQuery = MutableStateFlow("")

    val searchResults: StateFlow<List<ProcessUiModel>> = combine(
        _searchQuery.debounce(150), // Debounce by 150ms
        filteredProcesses
    ) { query, processes ->
        if (query.isEmpty()) {
            processes
        } else {
            processes.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.proc.cmdLine.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun setShowUserApps(value: Boolean) {
        _showUserApps.value = value
    }

    fun setShowSystemApps(value: Boolean) {
        _showSystemApps.value = value
    }

    fun setShowLinuxProcess(value: Boolean) {
        _showLinuxProcess.value = value
    }

    val uiProcesses = _uiProcesses.asStateFlow()
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
        viewModelScope.launch(Dispatchers.IO) {
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

                        val uiList = newProcesses.map { proc ->
                            async(Dispatchers.IO) {
                                val context = TaskManager.requireContext()
                                val name = getApkNameFromPackage(context, proc.cmdLine) ?: proc.name
                                val icon = getAppIconBitmap(context, proc.cmdLine)?.asImageBitmap()
                                val system = isSystemApp(context, proc.cmdLine)
                                val isApp = isAppInstalled(context, proc.cmdLine)
                                ProcessUiModel(proc, name, icon, system, isApp && !system, isApp = isApp)
                            }
                        }.awaitAll()

                        // Update state on Main thread to avoid snapshot issues
                        withContext(Dispatchers.Main) {
                            _uiProcesses.update { uiList }
                            isLoading.value = false
                        }
                    } catch (e: Exception) {
                        Log.e("ProcessList", "Failed to parse process list: ${e.message}")
                    }
                }
            }
        }

        viewModelScope.launch {
            refreshProcessesAuto()
        }
    }

    fun refreshProcessesManual() {
        isLoading.value = true
        viewModelScope.launch {
            send_daemon_messages.emit("LIST_PROCESS")
        }
    }

    fun refreshProcessesAuto() {
        viewModelScope.launch {
            send_daemon_messages.emit("LIST_PROCESS")
        }
    }
}