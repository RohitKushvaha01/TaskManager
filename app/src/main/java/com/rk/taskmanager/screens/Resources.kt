package com.rk.taskmanager.screens

import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.graphics.Typeface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.*
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.*
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
import com.rk.components.rememberMarker
import com.rk.taskmanager.shizuku.ShizukuUtil
import com.rk.components.SettingsToggle
import com.rk.taskmanager.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_POINTS = 120
private const val VISUAL_UPDATE_MS = 20L

private val RangeProvider = CartesianLayerRangeProvider.fixed(maxY = 100.0)
private val YDecimalFormat = DecimalFormat("#.##'%'")
private val StartAxisValueFormatter = CartesianValueFormatter.decimal(YDecimalFormat)
private val MarkerValueFormatter = DefaultCartesianMarker.ValueFormatter.default(YDecimalFormat)

val modelProducer = CartesianChartModelProducer()
val RamModelProducer = CartesianChartModelProducer()
val updating = AtomicBoolean(false)

val xValues = List(MAX_POINTS) { it.toDouble() }
val cpuYValues = mutableStateListOf<Number>().apply { repeat(MAX_POINTS) { add(0) } }
val ramYValues = mutableStateListOf<Number>().apply { repeat(MAX_POINTS) { add(0) } }

var lastCpuUsage by mutableFloatStateOf(0f)
var targetCpuUsage by mutableFloatStateOf(0f)
var currentCpuUsage by mutableFloatStateOf(0f)
var previousCpuUsage by mutableFloatStateOf(0f)

var lastRamUsage by mutableFloatStateOf(0f)
var targetRamUsage by mutableFloatStateOf(0f)
var currentRamUsage by mutableFloatStateOf(0f)
var previousRamUsage by mutableFloatStateOf(0f)

var CpuUsage by mutableIntStateOf(0)
var RamUsage by mutableIntStateOf(0)
var Ram by mutableStateOf("0mb of 0mb")
var state = mutableStateOf(ShizukuUtil.Error.NO_ERROR)

var lastCpuUpdateTime = 0L
var lastRamUpdateTime = 0L

fun smoothInterpolate(
    current: Float,
    target: Float,
    previous: Float,
    timeSinceUpdate: Long
): Float {
    // Low-pass filter approach - very smooth but responsive
    val alpha = when {
        timeSinceUpdate > 800 -> 0.4f   // Data getting stale
        timeSinceUpdate > 400 -> 0.2f   // Moderately stale
        else -> 0.1f                    // Fresh data, maximum smoothing
    }

    // Simple low-pass filter: output = alpha * input + (1-alpha) * previous_output
    return current * (1f - alpha) + target * alpha
}

suspend fun CoroutineScope.visualUpdater() {
    var frameCount = 0L

    while (isActive) {
        val start = System.currentTimeMillis()
        val timeSinceCpuUpdate = start - lastCpuUpdateTime
        val timeSinceRamUpdate = start - lastRamUpdateTime

        // Interpolate values
        currentCpuUsage = smoothInterpolate(
            currentCpuUsage,
            targetCpuUsage,
            previousCpuUsage,
            timeSinceCpuUpdate
        )

        currentRamUsage = smoothInterpolate(
            currentRamUsage,
            targetRamUsage,
            previousRamUsage,
            timeSinceRamUpdate
        )

        withContext(Dispatchers.Main) {
            // Update CPU graph with high resolution
            if (cpuYValues.size >= MAX_POINTS) cpuYValues.removeAt(0)
            cpuYValues.add(currentCpuUsage)

            // Batch updates every few frames for performance
            modelProducer.runTransaction {
                lineSeries { series(xValues, cpuYValues) }
            }

            // Update RAM graph with high resolution
            if (ramYValues.size >= MAX_POINTS) ramYValues.removeAt(0)
            ramYValues.add(currentRamUsage)

            RamModelProducer.runTransaction {
                lineSeries { series(xValues, ramYValues) }
            }

            CpuUsage = currentCpuUsage.toInt()
        }

        frameCount++

        val elapsed = System.currentTimeMillis() - start
        val remaining = VISUAL_UPDATE_MS - elapsed
        if (remaining > 0) delay(remaining) else delay(8) // Minimum 8ms for stability
    }
}

// Data fetcher with optimized timing
suspend fun CoroutineScope.dataFetcher(context: Context) {
    // Initialize timestamp
    lastCpuUpdateTime = System.currentTimeMillis()
    lastRamUpdateTime = System.currentTimeMillis()

    while (isActive) {
        val start = System.currentTimeMillis()

        // Fetch CPU data with Shizuku
        ShizukuUtil.withService {
            state.value = this
            if (this != ShizukuUtil.Error.NO_ERROR) {
                withContext(Dispatchers.Main) {
                    previousCpuUsage = targetCpuUsage
                    targetCpuUsage = 0f
                    lastCpuUpdateTime = System.currentTimeMillis()
                }
            } else {
                val cpuUsage = it?.getCpuUsage()?.toFloat() ?: 0f
                withContext(Dispatchers.Main) {
                    previousCpuUsage = targetCpuUsage
                    targetCpuUsage = cpuUsage
                    lastCpuUpdateTime = System.currentTimeMillis()
                }
            }
        }

        // Fetch RAM data
        val mi = MemoryInfo()
        val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(mi)

        val percentUsed = 100.0 - (mi.availMem.toDouble() / mi.totalMem.toDouble() * 100.0)

        withContext(Dispatchers.Main) {
            Ram = "${(mi.totalMem - mi.availMem) / (1024 * 1024)}MB of ${mi.totalMem / (1024 * 1024)}MB"
            RamUsage = percentUsed.toInt()
            previousRamUsage = targetRamUsage
            targetRamUsage = percentUsed.toFloat()
            lastRamUpdateTime = System.currentTimeMillis()
        }

        val elapsed = System.currentTimeMillis() - start
        val remaining = delayMs.toLong() - elapsed
        if (remaining > 0) delay(remaining) else delay(100)
    }
}

// Combined metrics updater
suspend fun CoroutineScope.metricsUpdater(context: Context) {
    launch { visualUpdater() }
    launch { dataFetcher(context) }
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

        SettingsToggle(
            label = "CPU - ${if (CpuUsage <= 0){"No Data"}else{"$CpuUsage%"}}",
            showSwitch = false,
            default = false
        )

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

        SettingsToggle(
            label = "RAM : $Ram ($RamUsage%)",
            showSwitch = false,
            default = false
        )

        Spacer(modifier = Modifier.padding(vertical = 16.dp))
    }
}