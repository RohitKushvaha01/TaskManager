package com.rk.taskmanager.screens.gpu

import android.graphics.Typeface
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.screens.cpu.InfoCard
import com.rk.taskmanager.screens.cpu.InfoItem
import com.rk.taskmanager.screens.cpu.MAX_GRAPH_POINTS
import com.rk.taskmanager.screens.cpu.MarkerValueFormatter
import com.rk.taskmanager.screens.cpu.RangeProvider
import com.rk.taskmanager.screens.cpu.StartAxisValueFormatter
import com.rk.taskmanager.screens.cpu.xValues
import com.rk.taskmanager.screens.selectedscreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val gpuYValues = ArrayDeque<Int>(MAX_GRAPH_POINTS).apply { repeat(MAX_GRAPH_POINTS) { add(0) } }

private val GpuModelProducer = CartesianChartModelProducer()

private var gpuUsage by mutableIntStateOf(-1)
private val mutex = Mutex()

suspend fun updateGpuGraph(usage: Int) {
    mutex.withLock {
        withContext(Dispatchers.Main){
            gpuUsage = usage
        }
        gpuYValues.removeFirst()
        gpuYValues.addLast(gpuUsage)

        if (selectedscreen.intValue == 0 && MainActivity.instance?.navControllerRef?.get()?.currentDestination?.route == SettingsRoutes.Home.route) {
            GpuModelProducer.runTransaction {
                lineSeries {
                    series(x = xValues, y = gpuYValues)
                }
            }
        }
    }

}




@Composable
fun GPU(modifier: Modifier = Modifier,viewModel: GpuViewModel) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gpuInfo by viewModel.gpuInfo.collectAsState()

    LaunchedEffect(Unit) {
        mutex.withLock {
            GpuModelProducer.runTransaction {
                lineSeries {
                    series(x = xValues, y = gpuYValues)
                }
            }
        }
    }

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
            GpuModelProducer,
            modifier,
            rememberVicoScrollState(scrollEnabled = false),
            animateIn = false,
            animationSpec = null,
        )


        SettingsToggle(
            description = "GPU - ${
                if (gpuUsage < 0) {
                    "No Data"
                } else {
                    "$gpuUsage%"
                }
            }",
            showSwitch = false,
            default = false
        )

        Spacer(modifier = Modifier.padding(vertical = 4.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            HorizontalDivider()

            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    InfoItem(
                        label = "Vendor",
                        value = gpuInfo?.vendor ?: "N/A",
                        highlighted = true
                    )

                    InfoItem(
                        label = "GPU Model",
                        value = gpuInfo?.renderer ?: "N/A",
                        highlighted = false
                    )

                    InfoItem(
                        label = "OpenGL",
                        value = gpuInfo?.openGlVersion ?: "N/A",
                        highlighted = false
                    )

                    InfoItem(
                        label = "Vulkan",
                        value = if (gpuInfo?.vulkanSupported == true){
                            "Supported"
                        }else{
                            "Not Supported"
                        },
                        highlighted = false
                    )


                    InfoItem(
                        label = "Vulkan API",
                        value = gpuInfo?.vulkanApiVersion ?: "N/A",
                        highlighted = false
                    )

                }
            }

        }

        Spacer(modifier = Modifier.padding(vertical = 16.dp))
    }
}

