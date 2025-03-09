package com.rk.taskmanager.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier,navController: NavController) {
    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        TopAppBar(title = {
            Text("Settings")
        }, navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "go back")
            }
        })
    }) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)){

        }
    }
}