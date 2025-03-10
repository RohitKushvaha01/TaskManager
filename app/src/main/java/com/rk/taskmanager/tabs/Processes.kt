package com.rk.taskmanager.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.taskmanager.shizuku.Proc
import com.rk.taskmanager.shizuku.ShizukuUtil
import com.rk.terminal.ui.components.SettingsToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProcessViewModel : ViewModel() {

    private val _state = MutableStateFlow(ShizukuUtil.Error.NO_ERROR)
    val state: StateFlow<ShizukuUtil.Error> = _state

    var processes = mutableStateListOf<Proc>()
        private set

    var isLoading = mutableStateOf(true)
        private set

    init {
        fetchProcesses()
    }

    private fun fetchProcesses() {
        println("loading...")
        viewModelScope.launch(Dispatchers.IO) {
            ShizukuUtil.withService { service ->
                _state.value = this

                // If there is any error, stop loading immediately
                if (this != ShizukuUtil.Error.NO_ERROR) {
                    isLoading.value = false
                    return@withService
                }

                val newProcesses = service!!.listPs()
                println("done...")

                // Update the list only if processes changed
                if (processes.size != newProcesses.size ||
                    processes.zip(newProcesses).any { it.first.pid != it.second.pid }) {

                    println("gggggg...")
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
        fetchProcesses()
    }
}


//todo fix infinite loading
//todo start loading on activity resume
//todo mmove process to the second button

@Composable
fun Processes(
    modifier: Modifier = Modifier,
    viewModel: ProcessViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.state.collectAsState()
    val processes = viewModel.processes
    val isLoading = viewModel.isLoading.value
    val listState = rememberLazyListState()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            ShizukuUtil.Error.NO_ERROR -> {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState
                    ) {
                        items(processes.size, key = { processes[it].pid }) { index ->
                            ProcessItem(processes[index])
                        }
                    }
                }
            }

            ShizukuUtil.Error.SHIZUKU_NOT_RUNNNING -> {
                Text("Shizuku Server not running")
            }

            ShizukuUtil.Error.PERMISSION_DENIED -> {
                Text("Shizuku permission denied")
            }

            else -> {
                Text("Unknown error, check logcat")
            }
        }
    }
}

@Composable
fun ProcessItem(proc: Proc) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp).clickable(enabled = true, onClick = {}),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp).padding(horizontal = 12.dp)
        ) {
            Text(
                text = proc.name,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "PID: ${proc.pid}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
