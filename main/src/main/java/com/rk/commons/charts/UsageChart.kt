package com.rk.commons.charts

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider

@Composable
fun UsageChart(
    modelProducer: CartesianChartModelProducer,
    lineColors: List<Color>,
    modifier: Modifier = Modifier,
    rangeProvider: CartesianLayerRangeProvider = ChartConfig.RangeProvider,
    valueFormatter: CartesianValueFormatter = ChartConfig.StartAxisValueFormatter,
    markerValueFormatter: DefaultCartesianMarker.ValueFormatter = ChartConfig.MarkerValueFormatter,
) {
    CartesianChartHost(
        rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    lineColors.map { color ->
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(color)),
                            areaFill = LineCartesianLayer.AreaFill.single(
                                fill(
                                    ShaderProvider.verticalGradient(
                                        intArrayOf(
                                            color.copy(alpha = 0.8f).toArgb(),
                                            Color.Transparent.toArgb()
                                        )
                                    )
                                )
                            ),
                            pointConnector = LineCartesianLayer.PointConnector.cubic(curvature = 0.7f),
                            stroke = LineCartesianLayer.LineStroke.continuous(thickness = 1.7.dp)
                        )
                    }
                ),
                rangeProvider = rangeProvider,
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = valueFormatter,
                label = TextComponent(
                    color = MaterialTheme.colorScheme.onSurface.toArgb(),
                    textSizeSp = 8f,
                    lineCount = 1,
                    typeface = Typeface.DEFAULT
                ),
                guideline = rememberAxisGuidelineComponent(
                    fill = fill(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    thickness = 1.dp
                ),
                tick = null,
                line = null,
            ),
            marker = rememberChartMarker(markerValueFormatter),
        ),
        modelProducer,
        modifier,
        rememberVicoScrollState(scrollEnabled = false),
        animateIn = false,
        animationSpec = null,
    )
}
