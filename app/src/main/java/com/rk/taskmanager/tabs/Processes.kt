package com.rk.taskmanager.tabs

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rk.taskmanager.shizuku.Proc
import com.rk.taskmanager.shizuku.ShizukuUtil

@Composable
fun Processes(modifier: Modifier = Modifier) {
    val context: Context = LocalContext.current
    var state by remember { mutableStateOf(ShizukuUtil.Error.NO_ERROR) }
    val processes = remember { mutableStateListOf<Proc>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        println("calling")
        ShizukuUtil.withService { service ->
            state = this

            processes.clear()
            processes.addAll(service!!.listPs())

            isLoading = false
        }
    }

    if (isLoading) {
        // Show a loading indicator
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        // Show the list of processes

        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(processes.size) { index ->
                    val process = processes[index]
                    Text(text = process.name)
                }
            }
        }



    }
}

