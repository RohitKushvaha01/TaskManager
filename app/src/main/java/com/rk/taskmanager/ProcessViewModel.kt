package com.rk.taskmanager

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.taskmanager.shizuku.Proc
import com.rk.taskmanager.shizuku.ShizukuUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ProcessViewModel : ViewModel() {

    private val _state = MutableStateFlow(ShizukuUtil.Error.NO_ERROR)
    val state: StateFlow<ShizukuUtil.Error> = _state

    var processes = mutableStateListOf<Proc>()
        private set

    var isLoading = mutableStateOf(true)
        private set


    private var loadingMutex = Mutex()
    init {
        viewModelScope.launch{
            fetchProcesses()
        }
    }

    private suspend fun fetchProcesses() = withContext(Dispatchers.IO) {
        loadingMutex.withLock{
            Log.i("ViewModel","Launching service...")
            ShizukuUtil.withService { service ->
                Log.i("ViewModel","Service Launched")
                _state.value = this

                // If there is any error, stop loading immediately
                if (this != ShizukuUtil.Error.NO_ERROR) {
                    isLoading.value = false
                    return@withService
                }

                val newProcesses = service!!.listPs()
                Log.d("ViewModel","Got process list...")

                // Update the list only if processes changed
                if (processes.size != newProcesses.size ||
                    processes.zip(newProcesses).any { it.first.pid != it.second.pid }) {

                    Log.d("ViewModel","Updating process list...")
                    viewModelScope.launch(Dispatchers.Main) {
                        processes.clear()
                        processes.addAll(newProcesses)
                        isLoading.value = false
                    }
                }
            }
        }
    }

    fun refreshProcesses() {
        isLoading.value = true
        viewModelScope.launch{
            fetchProcesses()
        }
    }

    fun refreshAuto(){
        if (processes.isEmpty() || state.value != ShizukuUtil.Error.NO_ERROR){
           refreshProcesses()
        }
    }
}