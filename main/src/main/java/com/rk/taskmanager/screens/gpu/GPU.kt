package com.rk.taskmanager.screens.gpu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.rk.commons.charts.GraphDataHandler
import com.rk.commons.charts.UsageChart
import com.rk.commons.ui.InfoCard
import com.rk.commons.ui.InfoItem
import com.rk.components.SettingsToggle
import com.rk.taskmanager.navControllerRef
import com.rk.taskmanager.screens.selectedscreen
import com.rk.taskmanager.settings.SettingsRoutes
import com.rk.commons.strings
import kotlin.math.roundToInt

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
    var showGpuInfoPopup by remember { mutableStateOf(false) }
    var gpuInfoIconBounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current

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
            description = if (gpuUsage < 0) null else stringResource(strings.gpu_usage_label, "$gpuUsage%"),
            showSwitch = false,
            default = false,
            descriptionContent = if (gpuUsage < 0) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(stringResource(strings.gpu_usage_label, stringResource(strings.no_data)))
                        IconButton(
                            onClick = { showGpuInfoPopup = true },
                            modifier = Modifier
                                .size(20.dp)
                                .onGloballyPositioned { coords ->
                                    gpuInfoIconBounds = coords.boundsInWindow()
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = stringResource(strings.gpu_usage_unavailable_help),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (showGpuInfoPopup) {
                            Popup(
                                popupPositionProvider = remember(gpuInfoIconBounds) {
                                    object : PopupPositionProvider {
                                        override fun calculatePosition(
                                            anchorBounds: IntRect,
                                            windowSize: IntSize,
                                            layoutDirection: LayoutDirection,
                                            popupContentSize: IntSize,
                                        ): IntOffset {
                                            return IntOffset(
                                                gpuInfoIconBounds.left.roundToInt(),
                                                gpuInfoIconBounds.bottom.roundToInt() + with(density) { 4.dp.roundToPx() }
                                            )
                                        }
                                    }
                                },
                                onDismissRequest = { showGpuInfoPopup = false }
                            ) {
                                Card(
                                    modifier = Modifier.width(220.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                ) {
                                    Text(
                                        text = stringResource(strings.gpu_usage_unavailable),
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                null
            }
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
