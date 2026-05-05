package com.rk.taskmanager.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.bridge.OpenSourceOnly
import com.rk.bridge.ProBridge
import com.rk.bridge.bridge
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.R
import com.rk.taskmanager.navControllerRef
import com.rk.taskmanager.screens.cpu.CPU
import com.rk.taskmanager.screens.gpu.GPU
import com.rk.taskmanager.screens.gpu.GpuViewModel
import com.rk.taskmanager.screens.ram.RAM
import com.rk.taskmanager.settings.SettingsRoutes
import com.rk.commons.strings

private data class ResourceTab(
    val labelRes: Int,
    val icon: TabIcon,
    val content: @Composable (
        Modifier,
        ProcessViewModel,
        GpuViewModel
    ) -> Unit
)


sealed class TabIcon {
    data class Res(val id: Int) : TabIcon()
    data class Vector(val image: ImageVector) : TabIcon()
}

private val tabs = listOf(


    ResourceTab(
        labelRes = strings.cpu,
        icon = TabIcon.Res(R.drawable.cpu_24px),
        content = { modifier, vm, _ ->
            CPU(modifier, vm)
        }
    ),

    ResourceTab(
        labelRes = strings.ram,
        icon = TabIcon.Res(R.drawable.memory_alt_24px),
        content = { modifier, vm, _ ->
            RAM(modifier, vm)
        }
    ),

    ResourceTab(
        labelRes = strings.gpu,
        icon = TabIcon.Res(R.drawable.cpu_24px),
        content = { modifier, _, gpuVm ->
            GPU(modifier, gpuVm)
        }
    ),

    ResourceTab(
        labelRes = strings.disk,
        icon = TabIcon.Vector(Icons.Outlined.Storage),
        content = { _, _, _ ->
            if (bridge != null) bridge!!.DiskScreen()
            else OpenSourceOnly()
        }
    ),

    ResourceTab(
        labelRes = strings.net,
        icon = TabIcon.Vector(Icons.Outlined.NetworkCheck),
        content = { _, _, _ ->
            if (bridge != null) bridge!!.NetScreen()
            else OpenSourceOnly()
        }
    ),



    ResourceTab(
        labelRes = strings.bat,
        icon = TabIcon.Vector(Icons.Outlined.BatteryChargingFull),
        content = { _, _, _ ->
            if (bridge != null) bridge!!.BatteryScreen()
            else OpenSourceOnly()
        }
    )



)

var currentResource by mutableIntStateOf(0)

@Composable
fun ResourceHostScreen(
    modifier: Modifier = Modifier,
    viewModel: ProcessViewModel,
    gpuViewModel: GpuViewModel
) {
    Row(modifier = modifier) {
        NavigationRail(
            modifier = Modifier.width(62.dp),
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            tabs.forEachIndexed { index, tab ->
                NavigationRailItem(
                    selected = currentResource == index,
                    onClick = { currentResource = index },
                    icon = {
                        when (val icon = tab.icon) {

                            is TabIcon.Res -> Icon(
                                painter = painterResource(icon.id),
                                contentDescription = null
                            )

                            is TabIcon.Vector -> Icon(
                                imageVector = icon.image,
                                contentDescription = null
                            )
                        }
                    },
                    label = {
                        Text(stringResource(tab.labelRes))
                    }
                )
            }
        }

        VerticalDivider()

        Box(modifier = Modifier.weight(1f)) {
            if (currentResource in tabs.indices) {
                tabs[currentResource].content(
                    Modifier.padding(horizontal = 2.dp).fillMaxSize(),
                    viewModel,
                    gpuViewModel
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(strings.not_implemented))
                }
            }
        }
    }
}