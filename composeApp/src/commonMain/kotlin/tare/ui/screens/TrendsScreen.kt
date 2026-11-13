package tare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tare.AppGraph
import tare.data.Source
import tare.data.WeighIn
import tare.data.composition
import tare.ui.Format
import tare.ui.Metric
import tare.ui.components.TimeChart
import tare.ui.components.TimePoint
import tare.ui.components.TopBar
import tare.ui.formatPlotted
import tare.ui.of
import tare.ui.theme.Tare
import tare.ui.theme.TareIcons
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private enum class Range(val label: String, val span: Duration?) {
    WEEK("1W", 7.days), MONTH("1M", 30.days), QUARTER("3M", 91.days), YEAR("1Y", 365.days), ALL("All", null)
}

@Composable
fun TrendsScreen(graph: AppGraph, initial: Metric, onBack: () -> Unit, onDetail: (WeighIn) -> Unit) {
    val c = Tare.colors
    val weighIns by graph.repo.weighIns.collectAsState(initial = remember { graph.repo.all() })
    val settings by graph.repo.settings.collectAsState()
    var metric by remember { mutableStateOf(initial) }
    var range by remember { mutableStateOf(Range.MONTH) }
    var selected by remember(metric, range) { mutableStateOf<Int?>(null) }

    val now = remember { Clock.System.now() }
    val start: Instant = range.span?.let { now - it } ?: (weighIns.firstOrNull()?.takenAt ?: now - 30.days)
    val rows = remember(weighIns, settings, metric, range) {
        weighIns.filter { it.takenAt >= start }.mapNotNull { w ->
            metric.of(w, settings.profile, settings.unit, w.composition(settings.profile))?.let { w to it }
        }
    }
    val points = rows.map { (w, v) -> TimePoint(w.takenAt.toEpochMilliseconds(), v.value) }
    val unitLabel = rows.firstOrNull()?.second?.unit.orEmpty()

    LazyColumn(Modifier.fillMaxSize().background(c.background)) {
        item {
            Column(Modifier.fillMaxWidth().background(c.headerBrush)) {
                TopBar("Trends", onBack)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Metric.entries.forEach { m ->
                        Pill(m.label, m == metric) { metric = m }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        item {
            Column(Modifier.padding(16.dp).clip(RoundedCornerShape(26.dp)).background(c.card).padding(vertical = 18.dp, horizontal = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(metric.label, style = Tare.section, color = c.ink)
                        if (unitLabel.isNotEmpty()) Text(unitLabel, style = Tare.caption, color = c.inkSoft)
                    }
                    Row(Modifier.clip(RoundedCornerShape(50)).background(c.tile).padding(3.dp)) {
                        Range.entries.forEach { r ->
                            Text(
                                r.label,
                                style = Tare.label,
                                color = if (r == range) c.ink else c.inkSoft,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (r == range) c.card else c.tile)
                                    .clickable { range = r }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                TimeChart(
                    points = points,
                    startMillis = start.toEpochMilliseconds(),
                    endMillis = now.toEpochMilliseconds(),
                    selected = selected,
                    onSelect = { selected = it },
                    valueText = { metric.formatPlotted(it, settings.unit) },
                    dateText = { Format.shortDate(Format.local(Instant.fromEpochMilliseconds(it)).date) },
                )
                if (points.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    val values = points.map { it.value }
                    Row {
                        Stat("Average", metric.formatPlotted(values.average(), settings.unit), Modifier.weight(1f))
                        Stat("Low", metric.formatPlotted(values.min(), settings.unit), Modifier.weight(1f))
                        Stat("High", metric.formatPlotted(values.max(), settings.unit), Modifier.weight(1f))
                        val change = values.last() - values.first()
                        Stat("Change", (if (change > 0) "+" else "") + metric.formatPlotted(change, settings.unit), Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(metric.about, style = Tare.caption, color = c.inkSoft)
            }
        }
        item {
            Text(
                "History",
                style = Tare.section,
                color = c.ink,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp),
            )
        }
        if (rows.isEmpty()) {
            item {
                Text(
                    if (metric.needsImpedance) "Nothing to show. Body composition needs a barefoot weigh-in and a complete profile." else "No weigh-ins in this range.",
                    style = Tare.body, color = c.inkSoft, modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        items(rows.reversed(), key = { it.first.id }) { (w, v) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.card)
                    .clickable { onDetail(w) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when (w.source) {
                        Source.MANUAL -> TareIcons.Pencil
                        else -> TareIcons.Scale
                    },
                    null, tint = c.inkFaint, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Format.relativeDay(w.takenAt), style = Tare.body.copy(fontWeight = FontWeight.Medium), color = c.ink)
                    Text(Format.time(w.takenAt), style = Tare.caption, color = c.inkSoft)
                }
                Text(v.text, style = Tare.body.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                if (v.unit.isNotEmpty()) Text(" ${v.unit}", style = Tare.caption, color = c.inkSoft)
            }
        }
        item { Spacer(Modifier.height(24.dp).windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Tare.colors
    Text(
        text,
        style = Tare.label,
        color = if (selected) c.card else c.ink,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.ink else c.tileOnHeader)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = Tare.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Tare.body.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1)
        Text(label, style = Tare.caption, color = c.inkSoft)
    }
}
