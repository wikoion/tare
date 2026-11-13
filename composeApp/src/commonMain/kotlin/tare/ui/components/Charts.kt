package tare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import tare.ui.theme.Tare

data class StripPoint(val value: Double, val valueText: String, val unit: String, val dateText: String, val yearText: String)

/**
 * The home screen's recent-history strip: one evenly spaced point per weigh-in, scrolled to the
 * latest. Tapping a point selects it and a rounded column highlights it with its value.
 */
@Composable
fun TrendStrip(
    points: List<StripPoint>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
) {
    val c = Tare.colors
    val measurer = rememberTextMeasurer()
    val spacing = 66.dp
    val edge = 18.dp
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val contentWidth = max(maxWidth, spacing * points.size + edge * 2)
        val scroll = rememberScrollState()
        val density = LocalDensity.current
        val viewportPx = with(density) { maxWidth.toPx() }
        val spPx = with(density) { spacing.toPx() }
        val leftPx = with(density) { contentWidth.toPx() - edge.toPx() } - spPx * points.size

        LaunchedEffect(points.size, selected) {
            val maxScroll = snapshotFlow { scroll.maxValue }.first { it > 0 }
            val selX = leftPx + spPx * (selected + 0.5f)
            val visible = selX > scroll.value + spPx / 2 && selX < scroll.value + viewportPx - spPx / 2
            if (selected == points.lastIndex) {
                scroll.animateScrollTo(maxScroll)
            } else if (!visible) {
                scroll.animateScrollTo((selX - viewportPx / 2).roundToInt().coerceIn(0, maxScroll))
            }
        }

        // The y-axis fits the points on screen (plus one either side), easing as you scroll.
        val window by remember(points, leftPx, viewportPx) {
            derivedStateOf {
                val first = floor((scroll.value - leftPx) / spPx).toInt() - 1
                val last = ceil((scroll.value + viewportPx - leftPx) / spPx).toInt()
                val slice = points.subList(first.coerceIn(0, points.size), (last + 1).coerceIn(0, points.size))
                    .ifEmpty { points }
                val lo = slice.minOfOrNull { it.value } ?: 0.0
                val hi = slice.maxOfOrNull { it.value } ?: 1.0
                val pad = max((hi - lo) * 0.18, 0.4)
                (lo - pad).toFloat() to (hi + pad).toFloat()
            }
        }
        val yLo by animateFloatAsState(window.first)
        val yHi by animateFloatAsState(window.second)

        Canvas(
            Modifier
                .horizontalScroll(scroll)
                .width(contentWidth)
                .fillMaxHeight()
                .pointerInput(points) {
                    detectTapGestures { pos ->
                        val left = size.width - edge.toPx() - spacing.toPx() * points.size
                        val i = ((pos.x - left) / spacing.toPx()).toInt()
                        if (i in points.indices) onSelect(i)
                    }
                },
        ) {
            if (points.isEmpty()) return@Canvas
            val sp = spacing.toPx()
            val left = size.width - edge.toPx() - sp * points.size
            fun x(i: Int) = left + sp * (i + 0.5f)

            val yearBand = 30.dp.toPx()
            val dateBand = 46.dp.toPx()
            val pillBottom = size.height - yearBand
            val plotTop = 92.dp.toPx()
            val plotBottom = pillBottom - dateBand - 22.dp.toPx()

            // Selection column.
            if (selected in points.indices) {
                val pw = 60.dp.toPx()
                drawRoundRect(
                    color = c.selection,
                    topLeft = Offset(x(selected) - pw / 2, 0f),
                    size = Size(pw, pillBottom),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                )
                val p = points[selected]
                drawCentered(measurer, p.valueText, x(selected), 14.dp.toPx(), TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.ink))
                drawCentered(measurer, p.unit, x(selected), 38.dp.toPx(), TextStyle(fontSize = 16.sp, color = c.ink))
                drawCentered(measurer, p.yearText, x(selected), pillBottom + 6.dp.toPx(), TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink))
            }

            // Line.
            val span = max(0.001f, yHi - yLo)
            fun y(v: Double) = plotBottom - ((v.toFloat() - yLo) / span) * (plotBottom - plotTop)

            val path = Path()
            points.forEachIndexed { i, p ->
                if (i == 0) {
                    path.moveTo(x(i), y(p.value))
                } else {
                    val mid = (x(i - 1) + x(i)) / 2
                    path.cubicTo(mid, y(points[i - 1].value), mid, y(p.value), x(i), y(p.value))
                }
            }
            clipRect(top = plotTop - 30.dp.toPx(), bottom = plotBottom + 30.dp.toPx()) {
                drawPath(path, c.ink, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                points.forEachIndexed { i, p ->
                    if (i == selected) {
                        drawCircle(Color.White, 8.dp.toPx(), Offset(x(i), y(p.value)))
                        drawCircle(c.ink, 5.5.dp.toPx(), Offset(x(i), y(p.value)))
                    } else {
                        drawCircle(c.ink, 4.5.dp.toPx(), Offset(x(i), y(p.value)))
                    }
                }
            }

            // Date labels.
            points.forEachIndexed { i, p ->
                val sel = i == selected
                drawCentered(
                    measurer, p.dateText, x(i), pillBottom - dateBand + 8.dp.toPx(),
                    TextStyle(
                        fontSize = if (sel) 17.sp else 15.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (sel) c.ink else c.inkSoft,
                    ),
                )
            }
        }
    }
}

data class TimePoint(val epochMillis: Long, val value: Double)

/**
 * A time-scaled line chart with a soft fill. Tap or drag to inspect a point; [label] formats
 * the inspected value and [axisLabel] the y-axis and date labels.
 */
@Composable
fun TimeChart(
    points: List<TimePoint>,
    startMillis: Long,
    endMillis: Long,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    valueText: (Double) -> String,
    dateText: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val c = Tare.colors
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(240.dp)
            .pointerInput(points, startMillis, endMillis) {
                fun nearest(px: Float): Int? {
                    if (points.isEmpty()) return null
                    val left = 8.dp.toPx()
                    val w = size.width - left - 52.dp.toPx()
                    val t = startMillis + ((px - left) / w * (endMillis - startMillis)).toLong()
                    return points.indices.minByOrNull { abs(points[it].epochMillis - t) }
                }
                detectTapGestures { onSelect(nearest(it.x)) }
            }
            .pointerInput(points, startMillis, endMillis) {
                fun nearest(px: Float): Int? {
                    if (points.isEmpty()) return null
                    val left = 8.dp.toPx()
                    val w = size.width - left - 52.dp.toPx()
                    val t = startMillis + ((px - left) / w * (endMillis - startMillis)).toLong()
                    return points.indices.minByOrNull { abs(points[it].epochMillis - t) }
                }
                detectDragGestures { change, _ -> onSelect(nearest(change.position.x)) }
            },
    ) {
        val left = 8.dp.toPx()
        val right = size.width - 52.dp.toPx()
        val top = 34.dp.toPx()
        val bottom = size.height - 28.dp.toPx()
        val labelStyle = TextStyle(fontSize = 12.sp, color = c.inkFaint)

        if (points.isEmpty()) {
            drawCentered(measurer, "No data in this range", size.width / 2, size.height / 2 - 10.dp.toPx(), TextStyle(fontSize = 15.sp, color = c.inkSoft))
            return@Canvas
        }
        val lo = points.minOf { it.value }
        val hi = points.maxOf { it.value }
        val pad = max((hi - lo) * 0.2, max(0.5, abs(hi) * 0.01))
        val yLo = lo - pad
        val yHi = hi + pad
        val span = max(1L, endMillis - startMillis)
        fun x(t: Long) = left + (t - startMillis).toFloat() / span * (right - left)
        fun y(v: Double) = (bottom - (v - yLo) / (yHi - yLo) * (bottom - top)).toFloat()

        // Grid and y labels.
        for (k in 0..3) {
            val v = yLo + (yHi - yLo) * k / 3
            val gy = y(v)
            drawLine(c.divider, Offset(left, gy), Offset(right, gy), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(valueText(v), labelStyle)
            drawText(layout, topLeft = Offset(right + 8.dp.toPx(), gy - layout.size.height / 2))
        }
        // X labels: start, middle, end.
        for ((i, t) in listOf(startMillis, (startMillis + endMillis) / 2, endMillis).withIndex()) {
            val layout = measurer.measure(dateText(t), labelStyle)
            val lx = when (i) {
                0 -> left
                1 -> (left + right - layout.size.width) / 2
                else -> right - layout.size.width
            }
            drawText(layout, topLeft = Offset(lx, bottom + 8.dp.toPx()))
        }

        val line = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) line.moveTo(x(p.epochMillis), y(p.value)) else line.lineTo(x(p.epochMillis), y(p.value))
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(x(points.last().epochMillis), bottom)
            lineTo(x(points.first().epochMillis), bottom)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.28f), c.accent.copy(alpha = 0f)), startY = top, endY = bottom))
        drawPath(line, c.accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        if (points.size <= 45) {
            points.forEach { drawCircle(c.accent, 3.dp.toPx(), Offset(x(it.epochMillis), y(it.value))) }
        }

        if (selected != null && selected in points.indices) {
            val p = points[selected]
            val px = x(p.epochMillis)
            drawLine(c.inkFaint, Offset(px, top - 6.dp.toPx()), Offset(px, bottom), strokeWidth = 1.dp.toPx())
            drawCircle(Color.White, 7.dp.toPx(), Offset(px, y(p.value)))
            drawCircle(c.ink, 4.5.dp.toPx(), Offset(px, y(p.value)))
            val text = "${valueText(p.value)}  ·  ${dateText(p.epochMillis)}"
            val layout = measurer.measure(text, TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink))
            val bw = layout.size.width + 20.dp.toPx()
            val bx = (px - bw / 2).coerceIn(0f, size.width - bw)
            drawRoundRect(c.tile, Offset(bx, 0f), Size(bw, 26.dp.toPx()), CornerRadius(13.dp.toPx()))
            drawText(layout, topLeft = Offset(bx + 10.dp.toPx(), 13.dp.toPx() - layout.size.height / 2))
        }
    }
}

private fun DrawScope.drawCentered(measurer: TextMeasurer, text: String, cx: Float, top: Float, style: TextStyle) {
    if (text.isEmpty()) return
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, top))
}
