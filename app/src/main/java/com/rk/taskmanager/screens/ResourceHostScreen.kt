package com.rk.taskmanager.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.screens.cpu.CPU

@androidx.compose.runtime.Composable
fun ResourceHostScreen(modifier: Modifier = Modifier,viewModel: ProcessViewModel) {
    Row {
        var currentResource by remember { mutableIntStateOf(0) }


        NavigationRail(modifier = Modifier.width(62.dp), windowInsets = WindowInsets(left = 0, right = 0, top = 0, bottom = 0)) {
            NavigationRailItem(selected = currentResource == 0, onClick = {
                currentResource = 0
            }, icon = {
                Icon(imageVector = Icons.Outlined.Poll,contentDescription = null)
            }, label = {
                Text("CPU")
            })

            NavigationRailItem(selected = currentResource == 1, onClick = {
                currentResource = 1
            }, icon = {
                Icon(imageVector = Icons.Outlined.Poll,contentDescription = null)
            }, label = {
                Text("RAM")
            })

//            NavigationRailItem(selected = currentResource == 2, onClick = {
//                currentResource = 2
//            }, icon = {
//                Icon(imageVector = Icons.Outlined.Poll,contentDescription = null)
//            }, label = {
//                Text("DISK")
//            })
//
//            NavigationRailItem(selected = currentResource == 3, onClick = {
//                currentResource = 3
//            }, icon = {
//                Icon(imageVector = Icons.Outlined.Poll,contentDescription = null)
//            }, label = {
//                Text("NET")
//            })
        }
        VerticalDivider()

        when(currentResource){
            0 -> {
                CPU(modifier = Modifier.padding(horizontal = 2.dp),viewModel)
            }

            1 -> {
                RAM(modifier = Modifier.padding(horizontal = 2.dp),viewModel)
            }

            else -> {
                Box {
                    Text("This feature is not implemented")
                }
            }
        }


    }
}