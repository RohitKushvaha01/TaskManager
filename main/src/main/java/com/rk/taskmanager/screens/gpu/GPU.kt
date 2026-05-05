package com.rk.taskmanager.screens.gpu

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.commons.charts.GraphDataHandler
import com.rk.commons.charts.UsageChart
import com.rk.commons.ui.InfoCard
import com.rk.commons.ui.InfoItem
import com.rk.components.SettingsToggle
import com.rk.taskmanager.navControllerRef
import com.rk.taskmanager.screens.selectedscreen
import com.rk.taskmanager.settings.SettingsRoutes
import com.rk.commons.strings

val gpuGraphHandler = GraphDataHandler(seriesCount = 1)
private var gpuUsage by mutableIntStateOf(-1)

suspend fun updateGpuGraph(usage: Int) {
    gpuUsage = usage
    gpuGraphHandler.update(usage) {
        selectedscreen.intValue == 0 && navControllerRef.get()?.currentDestination?.route == SettingsRoutes.Home.route
    }
}

@Composable
fun GPU(modifier: Modifier = Modifier, viewModel: GpuViewModel) {
    val gpuInfo by viewModel.gpuInfo.collectAsState()

    LaunchedEffect(Unit) {
        gpuGraphHandler.refresh()
    }

    Column(modifier.verticalScroll(rememberScrollState())) {
        UsageChart(
            modelProducer = gpuGraphHandler.modelProducer,
            lineColors = listOf(MaterialTheme.colorScheme.primary),
            modifier = modifier
        )

        SettingsToggle(
            description = stringResource(strings.gpu_usage_label, if (gpuUsage < 0) stringResource(strings.no_data) else "$gpuUsage%"),
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
                        label = stringResource(strings.vendor),
                        value = gpuInfo?.vendor ?: stringResource(strings.no_data),
                        highlighted = true
                    )

                    InfoItem(
                        label = stringResource(strings.gpu_model),
                        value = gpuInfo?.renderer ?: stringResource(strings.no_data),
                        highlighted = false
                    )

                    InfoItem(
                        label = stringResource(strings.opengl),
                        value = gpuInfo?.openGlVersion ?: stringResource(strings.no_data),
                        highlighted = false
                    )

                    InfoItem(
                        label = stringResource(strings.vulkan),
                        value = if (gpuInfo?.vulkanSupported == true) stringResource(strings.supported) else stringResource(strings.not_supported),
                        highlighted = false
                    )

                    InfoItem(
                        label = stringResource(strings.vulkan_api),
                        value = gpuInfo?.vulkanApiVersion ?: stringResource(strings.no_data),
                        highlighted = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.padding(vertical = 16.dp))
    }
}
