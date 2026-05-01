package com.rk.taskmanager.screens.cpu

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.commons.charts.GraphDataHandler
import com.rk.commons.charts.UsageChart
import com.rk.commons.ui.FrequencyInfo
import com.rk.commons.ui.InfoCard
import com.rk.commons.ui.InfoItem
import com.rk.commons.ui.SectionHeader
import com.rk.commons.utils.CpuInfoReader
import com.rk.components.SettingsToggle
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.daemon.daemon_messages
import com.rk.taskmanager.daemon.send_daemon_messages
import com.rk.taskmanager.navControllerRef
import com.rk.taskmanager.screens.selectedscreen
import com.rk.taskmanager.settings.SettingsRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

val cpuGraphHandler = GraphDataHandler(seriesCount = 1)

private val _cpuUsage = MutableStateFlow(0)
val cpuUsage = _cpuUsage.asStateFlow()

fun setCpuUsage(value: Int) {
    _cpuUsage.value = value
}

suspend fun updateCpuGraph(usage: Int) {
    setCpuUsage(usage)
    cpuGraphHandler.update(usage) {
        selectedscreen.intValue == 0 && navControllerRef.get()?.currentDestination?.route == SettingsRoutes.Home.route
    }
}

@Composable
fun CPU(modifier: Modifier = Modifier, viewModel: ProcessViewModel) {
    LaunchedEffect(Unit) {
        cpuGraphHandler.refresh()
    }

    var temperature by remember { mutableStateOf("N/A") }
    var uptime by remember { mutableStateOf("") }
    var cpuInfo by remember { mutableStateOf<CpuInfoReader.CpuInfo?>(null) }

    LaunchedEffect(Unit) {
        while (isActive) {
            send_daemon_messages.emit(JSONObject().apply { put("cmd", "CTEMP_PING") }.toString())
            uptime = CpuInfoReader.getUptimeFormatted()
            cpuInfo = CpuInfoReader.read()
            delay(2000)
        }
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            daemon_messages.collect { message ->
                try {
                    val json = JSONObject(message)
                    if (json.optString("type") == "CPU_TEMP") {
                        val temp = json.optInt("temp", -1)
                        if (temp > 0) {
                            temperature = temp.toString()
                        }
                    }
                } catch (e: Exception) {}
            }
        }
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
        UsageChart(
            modelProducer = cpuGraphHandler.modelProducer,
            lineColors = listOf(MaterialTheme.colorScheme.primary),
            modifier = modifier
        )

        val usage by cpuUsage.collectAsState()

        SettingsToggle(
            description = "CPU - ${if (usage < 0) "No Data" else "$usage%"}",
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
                    SectionHeader("Processor Information")

                    InfoItem(label = "SoC", value = cpuInfo?.soc ?: "N/A", highlighted = true)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem("Architecture", cpuInfo?.arch ?: "N/A")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem("ABI", cpuInfo?.abi ?: "N/A")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem("Cores", cpuInfo?.cores.toString())
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem("Governor", cpuInfo?.governor ?: "N/A")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoItem("Temperature", if (temperature.toIntOrNull() != null) "$temperature°C (estimated)" else temperature)
                        }
                    }
                }
            }

            HorizontalDivider()

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

            if (cpuInfo?.clusters?.isNotEmpty() == true) {
                cpuInfo?.clusters?.forEach { cluster ->
                    ClusterCard(cluster)
                }
            }
        }

        Spacer(modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
fun ClusterCard(cluster: CpuInfoReader.CpuCluster) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            FrequencyInfo(label = "Min", value = cluster.minFreq ?: "—", modifier = Modifier.weight(1f))
            cluster.currentFreq?.let { freq ->
                FrequencyInfo(label = "Current", value = freq, modifier = Modifier.weight(1f))
            }
            FrequencyInfo(label = "Max", value = cluster.maxFreq ?: "—", modifier = Modifier.weight(1f))
        }
    }
}