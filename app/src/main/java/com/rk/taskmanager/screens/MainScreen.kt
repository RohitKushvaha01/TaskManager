package com.rk.taskmanager.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.rk.DaemonResult
import com.rk.isConnected
import com.rk.startDaemon
import com.rk.taskmanager.MainActivity
import com.rk.taskmanager.settings.Settings
import com.rk.taskmanager.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

var selectedscreen = mutableIntStateOf(0)
var showFilter = mutableStateOf(false)
var showSystemApps = mutableStateOf(Settings.showSystemApps)
var showUserApps = mutableStateOf(Settings.showUserApps)
var showLinuxProcess = mutableStateOf(Settings.showLinuxProcess)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier,navController: NavController,viewModel: ProcessViewModel) {
    if (isConnected){
        Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
            TopAppBar(title = {
                Text(stringResource(strings.app_name))
            }, actions = {

                if (selectedscreen.intValue == 1){
                    IconButton(
                        modifier = Modifier.padding(8.dp),
                        onClick = {
                            showFilter.value = !showFilter.value
                        }) {
                        Icon(
                            imageVector = Filter,
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
    }else{
        LaunchedEffect(Unit) {
            if (Settings.workingMode != -1){
                MainActivity.scope?.launch(Dispatchers.Main) {
                    val daemonResult = startDaemon(context = MainActivity.instance!!, Settings.workingMode)
                    if (daemonResult != DaemonResult.OK){
                        delay(2000)
                        if (isConnected.not()){
                            println(daemonResult.name)
                            Toast.makeText(MainActivity.instance!!, daemonResult.message ?: "Unable to start daemon!", Toast.LENGTH_SHORT).show()
                            if (navController.currentDestination?.route != SettingsRoutes.SelectWorkingMode.route){
                                navController.navigate(SettingsRoutes.SelectWorkingMode.route)
                            }
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()){
            Column(modifier = Modifier.align(Alignment.Center)) {
                LinearProgressIndicator()
                Text("Waiting for daemon connection...")
                val context = LocalContext.current

                LaunchedEffect(isConnected) {
                    delay(5000)
                    if (isConnected.not()){
                        Toast.makeText(context, "TimeOut", Toast.LENGTH_SHORT).show()

                        if (navController.currentDestination?.route != SettingsRoutes.SelectWorkingMode.route){
                            navController.navigate(SettingsRoutes.SelectWorkingMode.route)
                        }
                    }
                }
            }
        }
    }


}


val Sort: ImageVector
    get() {
        if (_Sort != null) return _Sort!!

        _Sort = ImageVector.Builder(
            name = "Sort",
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

        return _Sort!!
    }

private var _Sort: ImageVector? = null


val Filter: ImageVector
    get() {
        if (_Filter != null) return _Filter!!

        _Filter = ImageVector.Builder(
            name = "Filter_alt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000))
            ) {
                moveTo(440f, 800f)
                quadToRelative(-17f, 0f, -28.5f, -11.5f)
                reflectiveQuadTo(400f, 760f)
                verticalLineToRelative(-240f)
                lineTo(168f, 224f)
                quadToRelative(-15f, -20f, -4.5f, -42f)
                reflectiveQuadToRelative(36.5f, -22f)
                horizontalLineToRelative(560f)
                quadToRelative(26f, 0f, 36.5f, 22f)
                reflectiveQuadToRelative(-4.5f, 42f)
                lineTo(560f, 520f)
                verticalLineToRelative(240f)
                quadToRelative(0f, 17f, -11.5f, 28.5f)
                reflectiveQuadTo(520f, 800f)
                close()
                moveToRelative(40f, -308f)
                lineToRelative(198f, -252f)
                horizontalLineTo(282f)
                close()
                moveToRelative(0f, 0f)
            }
        }.build()

        return _Filter!!
    }

private var _Filter: ImageVector? = null


