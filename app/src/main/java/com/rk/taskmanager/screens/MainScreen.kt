package com.rk.taskmanager.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.tabs.Processes
import com.rk.taskmanager.tabs.Resources

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier,navController: NavController,viewModel: ProcessViewModel) {
    var selectedscreen by remember { mutableIntStateOf(0) }
    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        TopAppBar(title = {
            Text("Task Manager")
        }, actions = {
            IconButton(
                modifier = Modifier.padding(8.dp),
                onClick = {
                    navController.navigate(SettingsRoutes.Settings.route)
                }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings"
                )
            }
        })
    },bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = selectedscreen == 0, onClick = {
                selectedscreen = 0
            }, icon = {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Processes"
                )
            }, label = {Text("Processes")})

            NavigationBarItem(selected = selectedscreen == 1, onClick = {
                selectedscreen = 1
            }, icon = {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = "Resources"
                )
            }, label = {Text("Resources")})
        }
    }) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)){
            when(selectedscreen){
                0 -> {
                    println("0")
                    viewModel.refreshAuto()
                    Processes(viewModel = viewModel, navController = navController)
                }
                1 -> {
                    Resources()
                }
            }
        }
    }
}