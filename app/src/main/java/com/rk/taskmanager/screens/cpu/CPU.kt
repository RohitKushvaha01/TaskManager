// ============================================
// FILE 1: CPU.kt
// ============================================
package com.rk.taskmanager.screens.cpu

import android.graphics.Typeface
import android.os.Handler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.SettingsRoutes
import com.rk.taskmanager.screens.selectedscreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.DecimalFormat
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

//global stuff
const val MAX_GRAPH_POINTS = 120
val RangeProvider = CartesianLayerRangeProvider.fixed(maxY = 100.0)
val YDecimalFormat = DecimalFormat("#.##'%'")
val StartAxisValueFormatter = CartesianValueFormatter.decimal(YDecimalFormat)
val MarkerValueFormatter = DefaultCartesianMarker.ValueFormatter.default(YDecimalFormat)

val xValues = List(MAX_GRAPH_POINTS) { it.toDouble() }

//CPU
val cpuYValues = ArrayDeque<Int>(MAX_GRAPH_POINTS).apply { repeat(MAX_GRAPH_POINTS) { add(0) } }

val CpuModelProducer = CartesianChartModelProducer()
private val cpuUsageAtomic = java.util.concurrent.atomic.AtomicInteger(0)
var cpuUsage by mutableIntStateOf(0)

suspend fun updateCpuGraph(usage: Int) {
    withContext(Dispatchers.Main){
        cpuUsage = usage
    }
    cpuYValues.removeFirst()
    cpuYValues.addLast(cpuUsage)

    if (selectedscreen.intValue == 0 && MainActivity.instance?.navControllerRef?.get()?.currentDestination?.route == SettingsRoutes.Home.route) {
        CpuModelProducer.runTransaction {
            lineSeries {
                series(x = xValues, y = cpuYValues)
            }
        }
    }
}

@Composable
fun CPU(modifier: Modifier = Modifier,viewModel: ProcessViewModel) {
    val lineColor = MaterialTheme.colorScheme.primary

    // Real-time data that updates periodically
    var temperature by remember { mutableStateOf<String?>(null) }
    var uptime by remember { mutableStateOf("") }

    // Static CPU info
    val cpuInfo = remember { CpuInfoReader.read() }

    // Update real-time data every 2 seconds
    LaunchedEffect(Unit) {
        while (isActive) {
            temperature = CpuInfoReader.getCpuTemperatureCelsius()
            uptime = CpuInfoReader.getUptimeFormatted()
            delay(2000)
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
            CpuModelProducer,
            modifier,
            rememberVicoScrollState(scrollEnabled = false),
            animateIn = false,
            animationSpec = null,
        )


        SettingsToggle(
            description = "CPU - ${
                if (cpuUsage <= 0) {
                    "No Data"
                } else {
                    "$cpuUsage%"
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

            // Main CPU Info Card
            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Processor Information")

                    InfoItem(
                        label = "SoC",
                        value = cpuInfo.soc,
                        highlighted = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem("Architecture", cpuInfo.arch)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem("ABI", cpuInfo.abi)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem("Cores", cpuInfo.cores.toString())
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem("Governor", cpuInfo.governor ?: "N/A")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem("Temperature", "$temperature°C")
                        }
                    }
                }
            }

            HorizontalDivider()

            // System Stats Card
            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("System Statistics")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem("Processes", viewModel.procCount.collectAsState().value.toString())
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem("Threads", viewModel.threadCount.collectAsState().value.toString())
                        }

                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem("Uptime", uptime)
                        }
                    }

                }
            }

            HorizontalDivider()

            // Clusters Section
            if (cpuInfo.clusters.isNotEmpty()) {

                cpuInfo.clusters.forEach { cluster ->
                    ClusterCard(cluster)
                }
            }
        }

        Spacer(modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
fun InfoCard(content: @Composable () -> Unit) {
    content()
}

@Composable
fun ClusterCard(cluster: CpuInfoReader.CpuCluster) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = cluster.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "${cluster.cores} cores",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FrequencyInfo(
                label = "Min",
                value = cluster.minFreq ?: "—",
                modifier = Modifier.weight(1f)
            )
            cluster.currentFreq?.let { freq ->
                FrequencyInfo(
                    label = "Current",
                    value = freq,
                    modifier = Modifier.weight(1f)
                )
            }
            FrequencyInfo(
                label = "Max",
                value = cluster.maxFreq ?: "—",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun FrequencyInfo(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun InfoItem(
    label: String,
    value: String,
    highlighted: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            style = if (highlighted) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}