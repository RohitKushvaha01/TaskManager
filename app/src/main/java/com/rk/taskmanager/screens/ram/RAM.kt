package com.rk.taskmanager.screens.ram

import android.app.ActivityManager
import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.rk.components.SettingsToggle
import com.rk.components.rememberMarker
import com.rk.taskmanager.MainActivity
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.TaskManager
import com.rk.taskmanager.navControllerRef
import com.rk.taskmanager.screens.cpu.MAX_GRAPH_POINTS
import com.rk.taskmanager.screens.cpu.MarkerValueFormatter
import com.rk.taskmanager.screens.cpu.RangeProvider
import com.rk.taskmanager.screens.cpu.StartAxisValueFormatter
import com.rk.taskmanager.screens.cpu.xValues
import com.rk.taskmanager.screens.selectedscreen
import com.rk.taskmanager.settings.SettingsRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

val ramYValues = ArrayDeque<Int>(MAX_GRAPH_POINTS).apply { repeat(MAX_GRAPH_POINTS) { add(0) } }
val swapYValues = ArrayDeque<Int>(MAX_GRAPH_POINTS).apply { repeat(MAX_GRAPH_POINTS) { add(0) } }


private val RamModelProducer = CartesianChartModelProducer()
//val ramYValues = mutableStateListOf<Number>().apply { repeat(MAX_POINTS) { add(0) } }
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

// Helper to format for display
fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val tb = gb * 1024

    return when {
        bytes >= tb.toLong() ->
            String.format(Locale.ENGLISH, "%.2f TB", bytes / tb)

        bytes >= gb.toLong() ->
            String.format(Locale.ENGLISH, "%.2f GB", bytes / gb)

        bytes >= mb.toLong() ->
            String.format(Locale.ENGLISH, "%.0f MB", bytes / mb)

        else ->
            String.format(Locale.ENGLISH, "%.0f KB", bytes / kb)
    }
}

private val mutex = Mutex()
suspend fun updateRamAndSwapGraph(usagePercent: Int, usageBytes: Long, totalBytes: Long) {
    mutex.withLock {
        val ramUsage = getSystemRamUsage(TaskManager.requireContext())

        // Update values
        RamUsage = ramUsage
        usedSwap = usageBytes
        totalSwap = totalBytes
        SwapUsage = usagePercent

        // Push new values into history

        ramYValues.removeFirst()
        ramYValues.addLast(ramUsage)

        swapYValues.removeFirst()
        swapYValues.addLast(usagePercent)


        // Update chart model with both lines
        if (selectedscreen.intValue == 0 && navControllerRef?.get()?.currentDestination?.route == SettingsRoutes.Home.route) {
            RamModelProducer.runTransaction {
                lineSeries {
                    series(x = xValues, y = ramYValues) // RAM line
                    series(x = xValues, y = swapYValues) // SWAP line
                }
            }
        }
    }

}


@Composable
fun RAM(modifier: Modifier = Modifier,viewModel: ProcessViewModel) {
    LaunchedEffect(Unit) {
        mutex.withLock {
            RamModelProducer.runTransaction {
                lineSeries {
                    series(x = xValues, y = ramYValues) // RAM line
                    series(x = xValues, y = swapYValues) // SWAP line
                }
            }
        }

    }
    Column(modifier.verticalScroll(rememberScrollState())) {
        val ramColor = MaterialTheme.colorScheme.primary
        val swapColor = MaterialTheme.colorScheme.tertiary

        CartesianChartHost(
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(ramColor)),
                            areaFill = LineCartesianLayer.AreaFill.single(
                                fill(
                                    ShaderProvider.verticalGradient(
                                        intArrayOf(
                                            ramColor.copy(alpha = 0.3f).toArgb(),
                                            Color.Transparent.toArgb()
                                        )
                                    )
                                )
                            )
                        ),
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(swapColor)),
                            areaFill = LineCartesianLayer.AreaFill.single(
                                fill(
                                    ShaderProvider.verticalGradient(
                                        intArrayOf(
                                            swapColor.copy(alpha = 0.3f).toArgb(),
                                            Color.Transparent.toArgb()
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    rangeProvider = RangeProvider,
                ),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = StartAxisValueFormatter,
                    label = TextComponent(
                        color = MaterialTheme.colorScheme.onSurface.toArgb(),
                        textSizeSp = 10f,
                        lineCount = 1,
                        typeface = Typeface.DEFAULT
                    ),
                    guideline = rememberAxisGuidelineComponent(),
                ),
                bottomAxis = null,
                marker = rememberMarker(MarkerValueFormatter),
            ),
            RamModelProducer,
            modifier,
            rememberVicoScrollState(scrollEnabled = false),
            animateIn = false,
            animationSpec = null
        )

        SettingsToggle(
            description = "RAM: ${formatBytes(usedRam)}/${formatBytes(totalRam)} ($RamUsage%)\n" +
                          "SWAP: ${formatBytes(usedSwap)}/${formatBytes(totalSwap)} ($SwapUsage%)",
            showSwitch = false,
            default = false
        )

        Spacer(modifier = Modifier.padding(vertical = 8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HorizontalDivider()

            //ram info goes here
        }






        Spacer(modifier = Modifier.padding(vertical = 16.dp))
    }
}