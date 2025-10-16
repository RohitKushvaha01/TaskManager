package com.rk.taskmanager.screens

import android.app.ActivityManager
import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.rk.components.SettingsToggle
import com.rk.components.rememberMarker
import com.rk.taskmanager.MainActivity
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.TaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.util.Locale

private const val MAX_POINTS = 120

private val RangeProvider = CartesianLayerRangeProvider.fixed(maxY = 100.0)
private val YDecimalFormat = DecimalFormat("#.##'%'")
private val StartAxisValueFormatter = CartesianValueFormatter.decimal(YDecimalFormat)
private val MarkerValueFormatter = DefaultCartesianMarker.ValueFormatter.default(YDecimalFormat)

val xValues = List(MAX_POINTS) { it.toDouble() }


val cpuYValues = ArrayDeque<Int>(MAX_POINTS).apply { repeat(MAX_POINTS) { add(0) } }
val ramYValues = ArrayDeque<Int>(MAX_POINTS).apply { repeat(MAX_POINTS) { add(0) } }
val swapYValues = ArrayDeque<Int>(MAX_POINTS).apply { repeat(MAX_POINTS) { add(0) } }



//CPU
val CpuModelProducer = CartesianChartModelProducer()
//val cpuYValues = mutableStateListOf<Number>().apply { repeat(MAX_POINTS) { add(0) } }
var CpuUsage by mutableIntStateOf(0)


//RAM
val RamModelProducer = CartesianChartModelProducer()
//val ramYValues = mutableStateListOf<Number>().apply { repeat(MAX_POINTS) { add(0) } }
var RamUsage by mutableIntStateOf(0)
var usedRam by mutableLongStateOf(0L)
var totalRam by mutableLongStateOf(0L)

//SWAP
val SwapModelProducer = CartesianChartModelProducer()
//val swapYValues = mutableStateListOf<Number>().apply { repeat(MAX_POINTS) { add(0) } }
var SwapUsage by mutableIntStateOf(0)

var usedSwap by mutableLongStateOf(0L)
var totalSwap by mutableLongStateOf(0L)


suspend fun getSystemRamUsage(context: Context): Int = withContext(Dispatchers.IO) {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)

    totalRam = memoryInfo.totalMem  // Keep as Long
    val availableRam = memoryInfo.availMem
    usedRam = totalRam - availableRam  // Keep as Long
    val usagePercentage = ((usedRam.toDouble() / totalRam.toDouble()) * 100).toInt()

    return@withContext usagePercentage
}

// Helper to format for display
fun formatRamMB(bytes: Long): String = "${bytes / (1024 * 1024)} MB"
fun formatRamGB(bytes: Long): String =
    String.format(Locale.ENGLISH, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))



suspend fun updateRamAndSwapGraph(usagePercent: Int, usageBytes: Long, totalBytes: Long) {
    val ramUsage = getSystemRamUsage(TaskManager.requireContext())

    // Update values
    RamUsage = ramUsage
    usedRam = totalRam - (totalRam * (100 - ramUsage) / 100)
    usedSwap = usageBytes
    totalSwap = totalBytes
    SwapUsage = usagePercent

    // Push new values into history

    ramYValues.removeFirst()
    ramYValues.addLast(ramUsage)

    swapYValues.removeFirst()
    swapYValues.addLast(usagePercent)


    // Update chart model with both lines
    if (selectedscreen.intValue == 0 && MainActivity.instance?.navControllerRef?.get()?.currentDestination?.route == SettingsRoutes.Home.route) {
    RamModelProducer.runTransaction {
        lineSeries {
            series(x = xValues, y = ramYValues) // RAM line
            series(x = xValues, y = swapYValues) // SWAP line
        }
    }
    }
}


suspend fun updateCpuGraph(usage: Int) {
    CpuUsage = usage
    //cpuYValues.removeAt(0)
    //cpuYValues.add(CpuUsage)

    cpuYValues.removeFirst()
    cpuYValues.addLast(CpuUsage)

    if (selectedscreen.intValue == 0 && MainActivity.instance?.navControllerRef?.get()?.currentDestination?.route == SettingsRoutes.Home.route) {
        CpuModelProducer.runTransaction {
            lineSeries {
                series(x = xValues, y = cpuYValues)
            }
        }
    }

}

@Composable
fun Resources(modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary

    Column(modifier.verticalScroll(rememberScrollState())) {

        CartesianChartHost(
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
                            areaFill = LineCartesianLayer.AreaFill.single(
                                fill(
                                    ShaderProvider.verticalGradient(
                                        intArrayOf(
                                            lineColor.copy(alpha = 0.4f).toArgb(),
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
            CpuModelProducer,
            modifier,
            rememberVicoScrollState(scrollEnabled = false),
            animateIn = false,
            animationSpec = null,
        )


        SettingsToggle(
            description = "CPU - ${
                if (CpuUsage <= 0) {
                    "No Data"
                } else {
                    "$CpuUsage%"
                }
            }",
            showSwitch = false,
            default = false
        )

        Spacer(modifier = Modifier.padding(vertical = 8.dp))
        val ramColor = MaterialTheme.colorScheme.primary
        val swapColor = MaterialTheme.colorScheme.tertiary


        HorizontalDivider()
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
            description = "RAM: ${formatRamMB(usedRam)}/${formatRamGB(totalRam)} ($RamUsage%)\nSWAP: ${
                formatRamMB(
                    usedSwap
                )
            }/${formatRamGB(totalSwap)} ($SwapUsage%)",
            showSwitch = false,
            default = false
        )


        Spacer(modifier = Modifier.padding(vertical = 16.dp))
    }
}