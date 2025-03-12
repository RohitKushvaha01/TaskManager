package com.rk.taskmanager.tabs

import android.graphics.PointF
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.patrykandpatrick.vico.compose.cartesian.*
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.*
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.taskmanager.shizuku.ShizukuUtil
import com.rk.terminal.ui.components.SettingsToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_POINTS = 60 // Number of visible data points
private val lineColor = Color(0xffa485e0)


private val YDecimalFormat = DecimalFormat("#.##'%'")
private val StartAxisValueFormatter =
    com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter.decimal(YDecimalFormat)

@Composable
fun Resources(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val modelProducer = remember { CartesianChartModelProducer() }
    val updating = remember { AtomicBoolean(false) }

    // Keep X values static (0 to MAX_POINTS-1)
    val xValues = remember { List(MAX_POINTS) { it.toDouble() } }
    val yValues = remember { mutableStateListOf<Number>().apply { repeat(MAX_POINTS) { add(0) } } }
    var lastCpuUsage by remember { mutableFloatStateOf(0f) }
    var CpuUsage by remember { mutableIntStateOf(0) }
    var state = remember { mutableStateOf(ShizukuUtil.Error.NO_ERROR) }


    LaunchedEffect(Unit) {
        suspend fun update() {
            if (!updating.compareAndSet(false, true)) return
            ShizukuUtil.withService {
                state.value = this
                if (this != ShizukuUtil.Error.NO_ERROR) {
                    scope.launch(Dispatchers.Main) {
                        if (yValues.size >= MAX_POINTS) {
                            yValues.removeAt(0)
                        }
                        yValues.add(0) // Set CPU usage to 0% if service fails
                        modelProducer.runTransaction {
                            lineSeries { series(xValues, yValues) }
                        }
                    }
                    updating.set(false)
                    return@withService
                }

                val cpuUsage = it!!.getCpuUsage()
                val smoothingFactor = 0.7f
                val smoothedCpuUsage = (cpuUsage * smoothingFactor) + (lastCpuUsage * (1 - smoothingFactor))
                lastCpuUsage = smoothedCpuUsage

                scope.launch(Dispatchers.Main) {
                    if (yValues.size >= MAX_POINTS) {
                        yValues.removeAt(0) // Always remove exactly one element
                    }
                    yValues.add(smoothedCpuUsage)
                    modelProducer.runTransaction {
                        lineSeries { series(xValues, yValues) }
                    }
                    CpuUsage = cpuUsage.toInt()
                }
                updating.set(false)
            }

        }

        while (isActive) {
            if (updating.get().not()){
                update()
                delay(200)
            }else{
                delay(50)
            }
        }
    }

    PreferenceGroup(heading = "CPU - ${if (CpuUsage <= 0){"No Data"}else{"$CpuUsage%"}}") {
        when (state.value) {
            ShizukuUtil.Error.NO_ERROR -> {
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
                            )
                        ),
                        startAxis = VerticalAxis.rememberStart(
                            valueFormatter = StartAxisValueFormatter,
                        ),
                        bottomAxis = null
                    ),
                    modelProducer,
                    modifier.padding(8.dp),
                    rememberVicoScrollState(scrollEnabled = true),
                    animateIn = false,
                )
            }
            ShizukuUtil.Error.SHIZUKU_NOT_RUNNNING -> {
                SettingsToggle(
                    label = "No Data",
                    description = "Shizuku not running",
                    showSwitch = false,
                    default = false
                )
            }
            ShizukuUtil.Error.PERMISSION_DENIED -> {
                SettingsToggle(
                    label = "No Data",
                    description = "Shizuku permission not granted",
                    showSwitch = false,
                    default = false
                )
            }
            else -> {
                SettingsToggle(
                    label = "No Data",
                    description = "Unknown Error please logcat for more info",
                    showSwitch = false,
                    default = false
                )
            }
        }

    }
}
