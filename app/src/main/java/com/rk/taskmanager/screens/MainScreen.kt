package com.rk.taskmanager.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.tabs.Processes
import com.rk.taskmanager.tabs.Resources
import com.rk.taskmanager.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.rk.taskmanager.settings.Settings

private var selectedscreen = mutableIntStateOf(0)
var showFilter = mutableStateOf(false)
var showSystemApps = mutableStateOf(Settings.showSystemApps)
var showLinuxProcess = mutableStateOf(Settings.showLinuxProcess)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier,navController: NavController,viewModel: ProcessViewModel) {
    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        TopAppBar(title = {
            Text("Task Manager")
        }, actions = {

            if (selectedscreen.intValue == 1){
                IconButton(
                    modifier = Modifier.padding(8.dp),
                    onClick = {
                        showFilter.value = !showFilter.value
                    }) {
                    Icon(
                        imageVector = Filter_list,
                        contentDescription = "Filter"
                    )
                }
            }


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
            NavigationBarItem(selected = selectedscreen.intValue == 0, onClick = {
                selectedscreen.intValue = 0
            }, icon = {
                Icon(
                    painter = painterResource(id = R.drawable.speed_24px),
                    contentDescription = "Resources"
                )
            }, label = {Text("Resources")})

            NavigationBarItem(selected = selectedscreen.intValue == 1, onClick = {
                selectedscreen.intValue = 1
            }, icon = {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Processes"
                )
            }, label = {Text("Processes")})
        }
    }) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)){
            LaunchedEffect(Unit) {
                viewModel.refreshAuto()
            }
            when(selectedscreen.intValue){
                0 -> {
                    Resources(modifier = Modifier.padding(horizontal = 4.dp))
                }
                1 -> {
                    Processes(viewModel = viewModel, navController = navController)
                }
            }
        }
    }
}


val Filter_list: ImageVector
    get() {
        if (_Filter_list != null) return _Filter_list!!

        _Filter_list = ImageVector.Builder(
            name = "Filter_list",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000))
            ) {
                moveTo(400f, 720f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(160f)
                verticalLineToRelative(80f)
                close()
                moveTo(240f, 520f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(480f)
                verticalLineToRelative(80f)
                close()
                moveTo(120f, 320f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(720f)
                verticalLineToRelative(80f)
                close()
            }
        }.build()

        return _Filter_list!!
    }

private var _Filter_list: ImageVector? = null

