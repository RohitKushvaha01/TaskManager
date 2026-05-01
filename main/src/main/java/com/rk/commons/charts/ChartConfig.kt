package com.rk.commons.charts

import android.graphics.Typeface
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import java.text.DecimalFormat

object ChartConfig {
    const val MAX_GRAPH_POINTS = 120
    val RangeProvider = CartesianLayerRangeProvider.fixed(maxY = 100.0)
    val YDecimalFormat = DecimalFormat("#.##'%'")
    val StartAxisValueFormatter = CartesianValueFormatter.decimal(YDecimalFormat)
    val MarkerValueFormatter = DefaultCartesianMarker.ValueFormatter.default(YDecimalFormat)
    val xValues = List(MAX_GRAPH_POINTS) { it.toDouble() }
}
