package com.rk.taskmanager

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.taskmanager.shizuku.Proc
import com.rk.taskmanager.shizuku.ShizukuUtil
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

data class ProcessUiModel(
    val proc: Proc,
    val name: String,
    val icon: ImageBitmap?,
    val isSystemApp: Boolean,
    val isInstalledApp: Boolean
)

class ProcessViewModel : ViewModel() {

    private val _state = MutableStateFlow(ShizukuUtil.Error.NO_ERROR)
    val state: StateFlow<ShizukuUtil.Error> = _state

    var processes = mutableStateListOf<Proc>()
        private set

    private val _uiProcesses = MutableStateFlow<List<ProcessUiModel>>(emptyList())
    val uiProcesses: StateFlow<List<ProcessUiModel>> = _uiProcesses

    var isLoading = mutableStateOf(true)

    private var loadingMutex = Mutex()

    init {
        viewModelScope.launch {
            fetchProcesses()
        }
    }

    private suspend fun fetchProcesses() = withContext(Dispatchers.IO) {
        loadingMutex.withLock {
            Log.i("ViewModel", "Launching service...")
            ShizukuUtil.withService { service ->

                Log.i("ViewModel", "Service Launched")
                _state.value = this

                if (this != ShizukuUtil.Error.NO_ERROR) {
                    isLoading.value = false
                    return@withService
                }

                val newProcesses = service!!.listPs().sortedWith(
                    compareByDescending<Proc> { it.cpuUsage }
                        .thenByDescending { it.memoryUsageKb }
                )

                Log.d("ViewModel", "Got process list...")

                if (processes.size != newProcesses.size ||
                    processes.zip(newProcesses).any { it.first.pid != it.second.pid }) {

                    Log.d("ViewModel", "Updating process list...")

                    // Precompute UI models
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
                }
            }
        }
    }

    fun refreshProcesses() {
        isLoading.value = true
        viewModelScope.launch {
            fetchProcesses()
        }
    }

    fun refreshAuto() {
        if (processes.isEmpty() || state.value != ShizukuUtil.Error.NO_ERROR) {
            refreshProcesses()
        }
    }
}
