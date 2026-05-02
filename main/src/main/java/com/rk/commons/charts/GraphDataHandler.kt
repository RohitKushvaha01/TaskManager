package com.rk.commons.charts

import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GraphDataHandler(
    private val seriesCount: Int = 1,
    private val maxPoints: Int = ChartConfig.MAX_GRAPH_POINTS
) {
    private val mutex = Mutex()
    val modelProducer = CartesianChartModelProducer()
    private val seriesData = List(seriesCount) {
        ArrayDeque<Int>(maxPoints).apply { repeat(maxPoints) { add(0) } }
    }

    suspend fun update(vararg newValues: Int, updateCondition: () -> Boolean = { true }) {
        mutex.withLock {
            newValues.forEachIndexed { index, value ->
                if (index < seriesCount) {
                    seriesData[index].removeFirst()
                    seriesData[index].addLast(value)
                }
            }
            
            if (updateCondition()) {
                modelProducer.runTransaction {
                    lineSeries {
                        seriesData.forEach { data ->
                            series(x = ChartConfig.xValues, y = data.toList())
                        }
                    }
                }
            }
        }
    }

    suspend fun reset() {
        mutex.withLock {
            // Reset internal data
            seriesData.forEach { deque ->
                deque.clear()
                repeat(maxPoints) { deque.addLast(0) }
            }

            // Update chart
            modelProducer.runTransaction {
                lineSeries {
                    seriesData.forEach { data ->
                        series(
                            x = ChartConfig.xValues,
                            y = data.toList()
                        )
                    }
                }
            }
        }
    }
    
    suspend fun refresh() {
        mutex.withLock {
            modelProducer.runTransaction {
                lineSeries {
                    seriesData.forEach { data ->
                        series(x = ChartConfig.xValues, y = data.toList())
                    }
                }
            }
        }
    }
}
