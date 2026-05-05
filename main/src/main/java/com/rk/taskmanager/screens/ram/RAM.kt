package com.rk.taskmanager.screens.ram

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.commons.charts.GraphDataHandler
import com.rk.commons.charts.UsageChart
import com.rk.commons.utils.FormatUtils
import com.rk.components.SettingsToggle
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.navControllerRef
import com.rk.taskmanager.screens.selectedscreen
import com.rk.taskmanager.settings.SettingsRoutes
import com.rk.commons.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val ramGraphHandler = GraphDataHandler(seriesCount = 2)

var RamUsage by mutableIntStateOf(0)
var usedRam by mutableLongStateOf(0L)
var totalRam by mutableLongStateOf(0L)

var SwapUsage by mutableIntStateOf(0)
var usedSwap by mutableLongStateOf(0L)
var totalSwap by mutableLongStateOf(0L)

suspend fun getSystemRamUsage(context: Context): Int = withContext(Dispatchers.IO) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)

    totalRam = info.totalMem
    val available = info.availMem
    usedRam = totalRam - available

    ((usedRam.toDouble() / totalRam.toDouble()) * 100)
        .toInt()
        .coerceIn(0, 100)
}

suspend fun updateRamAndSwapGraph(usagePercent: Int, usageBytes: Long, totalBytes: Long) {
    val ramUsage = getSystemRamUsage(TaskManager.requireContext())

    RamUsage = ramUsage
    usedSwap = usageBytes
    totalSwap = totalBytes
    SwapUsage = usagePercent

    ramGraphHandler.update(ramUsage, usagePercent) {
        selectedscreen.intValue == 0 && navControllerRef.get()?.currentDestination?.route == SettingsRoutes.Home.route
    }
}

@Composable
fun RAM(modifier: Modifier = Modifier, viewModel: ProcessViewModel) {
    LaunchedEffect(Unit) {
        ramGraphHandler.refresh()
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
        val ramColor = MaterialTheme.colorScheme.primary
        val swapColor = MaterialTheme.colorScheme.tertiary

        UsageChart(
            modelProducer = ramGraphHandler.modelProducer,
            lineColors = listOf(ramColor, swapColor),
            modifier = modifier
        )

        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                stringResource(
                    strings.ram_label,
                    FormatUtils.formatBytes(usedRam),
                    FormatUtils.formatBytes(totalRam),
                    RamUsage
                ),
                color = ramColor,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(
                    strings.swap_label,
                    FormatUtils.formatBytes(usedSwap),
                    FormatUtils.formatBytes(totalSwap),
                    SwapUsage
                ),
                color = swapColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.padding(vertical = 8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HorizontalDivider()
        }

        Spacer(modifier = Modifier.padding(vertical = 16.dp))
    }
}