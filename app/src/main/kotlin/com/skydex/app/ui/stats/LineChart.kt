package com.skydex.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** One chart sample: x is Unix millis, y the value. */
data class ChartPoint(val x: Long, val y: Double)

/** A single-series line chart drawn on a Canvas, labelled with its min/max value and date range. */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    formatValue: (Double) -> String,
    formatDate: (Long) -> String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (points.isEmpty()) return
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val minX = points.first().x
    val maxX = points.last().x
    val lineColor = color
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        Text(formatValue(maxY), style = labelStyle, color = labelColor)
        Canvas(Modifier.fillMaxWidth().height(140.dp).padding(vertical = 4.dp)) {
            fun offset(p: ChartPoint): Offset {
                val fx = if (maxX == minX) 0.5f else (p.x - minX).toFloat() / (maxX - minX)
                val fy = if (maxY == minY) 0.5f else ((p.y - minY) / (maxY - minY)).toFloat()
                return Offset(fx * size.width, (1 - fy) * size.height)
            }
            drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f))
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height))
            if (points.size == 1) {
                drawCircle(lineColor, radius = 4.dp.toPx(), center = offset(points.single()))
            } else {
                val path = Path()
                points.forEachIndexed { i, p ->
                    val o = offset(p)
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(
                    path,
                    lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        Text(formatValue(minY), style = labelStyle, color = labelColor)
        Row(Modifier.fillMaxWidth()) {
            Text(formatDate(minX), style = labelStyle, color = labelColor)
            Spacer(Modifier.weight(1f))
            Text(formatDate(maxX), style = labelStyle, color = labelColor)
        }
    }
}
