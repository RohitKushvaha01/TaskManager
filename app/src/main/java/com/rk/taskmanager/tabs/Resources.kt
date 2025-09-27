package com.rk.taskmanager.tabs

import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.graphics.PointF
import android.graphics.Typeface
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Debug
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.getSystemService
import com.patrykandpatrick.vico.compose.cartesian.*
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.*
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.rememberMarker
import com.rk.taskmanager.shizuku.ShizukuUtil
import com.rk.components.SettingsToggle
import com.rk.taskmanager.MainActivity
import com.rk.taskmanager.screens.delayMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_POINTS = 100


private val RangeProvider = CartesianLayerRangeProvider.fixed(maxY = 100.0)
private val YDecimalFormat = DecimalFormat("#.##'%'")
private val StartAxisValueFormatter =
    com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter.decimal(YDecimalFormat)
private val MarkerValueFormatter = DefaultCartesianMarker.ValueFormatter.default(YDecimalFormat)


val modelProducer = CartesianChartModelProducer()
val RamModelProducer = CartesianChartModelProducer()
val updating = AtomicBoolean(false)

// Keep X values static (0 to MAX_POINTS-1)
val xValues = List(MAX_POINTS) { it.toDouble() }
// Separate yValues lists
val cpuYValues = mutableStateListOf<Number>().apply { repeat(MAX_POINTS) { add(0) } }
val ramYValues = mutableStateListOf<Number>().apply { repeat(MAX_POINTS) { add(0) } }

var lastCpuUsage by  mutableFloatStateOf(0f)
var CpuUsage by mutableIntStateOf(0)
var RamUsage by mutableIntStateOf(0)
var Ram by mutableStateOf("0mb of 0mb")
var state = mutableStateOf(ShizukuUtil.Error.NO_ERROR)


suspend fun CoroutineScope.update() {
    if (!updating.compareAndSet(false, true)) return
    ShizukuUtil.withService {
        state.value = this
        if (this != ShizukuUtil.Error.NO_ERROR) {
            launch(Dispatchers.Main) {
                if (cpuYValues.size >= MAX_POINTS) {
                    cpuYValues.removeAt(0)
                }
                cpuYValues.add(0) // Set CPU usage to 0% if service fails
                modelProducer.runTransaction {
                    lineSeries { series(xValues,cpuYValues) }
                }
            }
            updating.set(false)
            return@withService
        }

        val cpuUsage = it!!.getCpuUsage()
        val smoothingFactor = 0.1f

        val valueToAdd = if (cpuYValues.any { it.toFloat() != 0f } && cpuYValues.size >= MAX_POINTS) {
            val smoothed = (cpuUsage * smoothingFactor) + (lastCpuUsage * (1 - smoothingFactor))
            lastCpuUsage = smoothed
            smoothed
        } else {
            lastCpuUsage = cpuUsage.toFloat()
            cpuUsage.toFloat()
        }


        launch(Dispatchers.Main) {
            if (cpuYValues.size >= MAX_POINTS) {
                cpuYValues.removeAt(0)
            }
            cpuYValues.add(valueToAdd)
            modelProducer.runTransaction {
                lineSeries { series(xValues, cpuYValues) }
            }
            CpuUsage = cpuUsage.toInt()
        }
        updating.set(false)
    }

}


suspend fun CoroutineScope.ramUpdater(context: Context){
    while (isActive){
        delay(delayMs.toLong())
        val mi = MemoryInfo()
        val activityManger = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        activityManger.getMemoryInfo(mi)

        val percentAvail = mi.availMem.toDouble() / mi.totalMem.toDouble() * 100.0

        val percentUsed = 100.0 - percentAvail

        withContext(Dispatchers.Main) {
            Ram = "${(mi.totalMem - mi.availMem) / (1024 * 1024)}MB of ${mi.totalMem / (1024 * 1024)}MB"

            RamUsage = percentUsed.toInt()

            RamModelProducer.runTransaction {
                lineSeries {
                    series(xValues, ramYValues.apply {
                        if (size >= MAX_POINTS) removeAt(0)
                        add(RamUsage)
                    })
                }
            }
        }

    }
}

suspend fun CoroutineScope.cpuUpdater(){
    while (isActive) {
        if (updating.get().not()){
            delay(delayMs.toLong())
            update()
        }else{
            delay(20)
        }
    }
}

@Composable
fun Resources(modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary

    Column(modifier.verticalScroll(rememberScrollState())) {
        Column() {
            when (state.value) {
                ShizukuUtil.Error.NO_ERROR -> {
                    if (CpuUsage <= 0 && cpuYValues.isEmpty()){
                        SettingsToggle(
                            label = "No Data",
                            description = "Waiting for response...",
                            showSwitch = false,
                            default = false
                        )
                    }else{
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
                            modelProducer,
                            modifier,
                            rememberVicoScrollState(scrollEnabled = false),
                            animateIn = false,
                            animationSpec = null,
                        )
                    }

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

                ShizukuUtil.Error.SHIZUKU_TIMEOUT -> {
                    SettingsToggle(
                        label = "No Data",
                        description = "Shizuku not running (connection timeout)",
                        showSwitch = false,
                        default = false
                    )
                }

                ShizukuUtil.Error.NOT_INSTALLED -> {
                    SettingsToggle(
                        label = "No Data",
                        description = "Shizuku not installed",
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
        SettingsToggle(label = "CPU - ${if (CpuUsage <= 0){"No Data"}else{"$CpuUsage%"}}",
            showSwitch = false,
            default = false)
        

        Column() {
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
                RamModelProducer,
                modifier,
                rememberVicoScrollState(scrollEnabled = false),
                animateIn = false,
                animationSpec = null
            )
        }

        SettingsToggle(label = "RAM : $Ram ($RamUsage%)",
            showSwitch = false,
            default = false)

        Spacer(modifier = Modifier.padding(vertical = 16.dp))
    }


}
