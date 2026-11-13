package tare.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tare.AppGraph
import tare.ble.ScaleStatus
import tare.data.Settings
import tare.data.Source
import tare.data.WeighIn
import tare.data.composition
import tare.platform.openAppSettings
import tare.platform.shareFile
import tare.data.CsvExport
import tare.ui.Format
import tare.ui.Metric
import tare.ui.components.HeaderTile
import tare.ui.components.IconButtonSoft
import tare.ui.components.MetricTile
import tare.ui.components.SoftButton
import tare.ui.components.StripPoint
import tare.ui.components.TrendStrip
import tare.ui.of
import tare.ui.theme.Tare
import tare.ui.theme.TareIcons
import tare.units.WeightFormat
import tare.units.WeightUnit
import kotlin.math.abs

@Composable
fun HomeScreen(
    graph: AppGraph,
    onTrends: (Metric) -> Unit,
    onSettings: () -> Unit,
    onPair: () -> Unit,
    onGoal: () -> Unit,
    onLog: () -> Unit,
    onDetail: (WeighIn) -> Unit,
    scrollToEnd: Boolean = false,
) {
    val c = Tare.colors
    val weighIns by graph.repo.weighIns.collectAsState(initial = remember { graph.repo.all() })
    val settings by graph.repo.settings.collectAsState()
    val status by graph.scale.status.collectAsState()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { graph.scale.saved.collect { selectedId = it.id } }

    val recent = weighIns.takeLast(90)
    val selectedIndex = recent.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: recent.lastIndex
    val current = recent.getOrNull(selectedIndex)
    val previous = recent.getOrNull(selectedIndex - 1)
    val unit = settings.unit

    val scroll = rememberScrollState()
    if (scrollToEnd) LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(c.background).verticalScroll(scroll)) {
        Column(Modifier.fillMaxWidth().background(c.headerBrush).windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tare", style = Tare.title.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold), color = c.ink)
                    StatusLine(status, settings, onPair)
                }
                IconButtonSoft(TareIcons.Share, "Export CSV", onClick = {
                    shareFile("tare-weigh-ins.csv", CsvExport.build(graph.repo.all(), settings.profile))
                })
                IconButtonSoft(TareIcons.Settings, "Settings", onClick = onSettings)
            }

            if (recent.isEmpty()) {
                EmptyHeader(status, settings, onPair)
            } else {
                val points = remember(recent, unit) {
                    recent.map { w ->
                        val d = WeightFormat.compact(w.weightKg, unit)
                        val date = Format.local(w.takenAt).date
                        StripPoint(
                            value = Metric.WEIGHT.of(w, settings.profile, unit, null)!!.value,
                            valueText = d.value,
                            unit = d.unit,
                            dateText = Format.numericDate(date),
                            yearText = date.year.toString(),
                        )
                    }
                }
                TrendStrip(points, selectedIndex, onSelect = { selectedId = recent[it].id }, modifier = Modifier.padding(top = 8.dp))
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderTile(TareIcons.Trends, "Trends", "Every metric", Modifier.weight(1f)) { onTrends(Metric.WEIGHT) }
                HeaderTile(TareIcons.Flag, "Goal", goalSubtitle(settings, weighIns.lastOrNull()), Modifier.weight(1f), onClick = onGoal)
            }
            Spacer(Modifier.height(30.dp))
        }

        Column(
            Modifier
                .offset(y = (-24).dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 30.dp, bottomEnd = 30.dp))
                .background(c.card)
                .padding(20.dp),
        ) {
            ReadingHeader(current, previous, status, settings, weighIns, onDetail)
            Spacer(Modifier.height(20.dp))
            val comp = current?.composition(settings.profile)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (metric in listOf(Metric.WEIGHT, Metric.BMI, Metric.BODY_FAT)) {
                    val v = current?.let { metric.of(it, settings.profile, unit, comp) }
                    MetricTile(
                        label = metric.label,
                        value = v?.text ?: "—",
                        unit = v?.unit.orEmpty(),
                        rating = v?.rating,
                        modifier = Modifier.weight(1f),
                        onClick = { onTrends(metric) },
                    )
                }
            }
            CompositionNotice(current, settings, onSettings)
            Spacer(Modifier.height(16.dp))
            SoftButton("Log weight", TareIcons.Pencil, onClick = onLog)
        }

        val comp = current?.composition(settings.profile)
        if (current != null && comp != null) {
            Column(Modifier.offset(y = (-12).dp).padding(horizontal = 0.dp)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.card).padding(20.dp),
                ) {
                    Text("Body composition", style = Tare.section, color = c.ink)
                    Text("Estimated from bioimpedance", style = Tare.caption, color = c.inkSoft)
                    Spacer(Modifier.height(14.dp))
                    val more = Metric.entries.filter { it.needsImpedance && it != Metric.BODY_FAT }
                    more.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                            pair.forEach { metric ->
                                val v = metric.of(current, settings.profile, unit, comp)
                                MetricTile(metric.label, v?.text ?: "—", v?.unit.orEmpty(), v?.rating, Modifier.weight(1f)) { onTrends(metric) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Text(
            "Body composition figures are bioimpedance estimates, not medical measurements.",
            style = Tare.caption,
            color = c.inkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
    // Backs the status bar once the header has scrolled away.
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .alpha((scroll.value / 300f).coerceIn(0f, 1f))
            .background(c.background),
    )
    }
}

@Composable
private fun StatusLine(status: ScaleStatus, settings: Settings, onPair: () -> Unit) {
    val c = Tare.colors
    val (text, color, action) = when (status) {
        ScaleStatus.Starting -> Triple("Starting…", c.inkFaint, null)
        ScaleStatus.Unsupported -> Triple("Bluetooth isn't available here", c.inkFaint, null)
        ScaleStatus.BluetoothOff -> Triple("Bluetooth is off", c.chip(tare.body.Rating.Tone.CAUTION).second, null)
        ScaleStatus.Unauthorized -> Triple("Allow Bluetooth in Settings ›", c.chip(tare.body.Rating.Tone.CAUTION).second, { openAppSettings() })
        ScaleStatus.NotPaired -> Triple("No scale paired ›", c.inkSoft, onPair)
        ScaleStatus.Pairing -> Triple("Looking for scales…", c.inkSoft, null)
        ScaleStatus.Waiting -> Triple("${settings.pairedScale?.name ?: "Scale"} · step on to weigh", c.inkSoft, null)
        ScaleStatus.Connected -> Triple("${settings.pairedScale?.name ?: "Scale"} connected", c.accent, null)
        is ScaleStatus.Weighing -> Triple("Weighing…", c.accent, null)
    }
    val live = status is ScaleStatus.Connected || status is ScaleStatus.Weighing
    val pulse = rememberInfiniteTransition()
    val alpha by pulse.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse))
    Row(
        Modifier.padding(top = 2.dp).then(if (action != null) Modifier.clickable(onClick = action) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).alpha(if (live) alpha else 1f).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Tare.label, color = color)
    }
}

@Composable
private fun EmptyHeader(status: ScaleStatus, settings: Settings, onPair: () -> Unit) {
    val c = Tare.colors
    Column(
        Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(c.tileOnHeader), contentAlignment = Alignment.Center) {
            Icon(TareIcons.Scale, null, tint = c.accent, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (settings.pairedScale == null) "Pair your scale to start" else "Step on your scale",
            style = Tare.section, color = c.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (settings.pairedScale == null) {
                "Weigh-ins arrive here automatically once it's paired."
            } else {
                "Barefoot, so it can measure body composition too. Your first weigh-in shows up here."
            },
            style = Tare.body, color = c.inkSoft, textAlign = TextAlign.Center,
        )
        if (settings.pairedScale == null && status != ScaleStatus.Unsupported) {
            Spacer(Modifier.height(12.dp))
            Text("Pair a scale ›", style = Tare.body.copy(fontWeight = FontWeight.SemiBold), color = c.accent, modifier = Modifier.clickable(onClick = onPair))
        }
    }
}

@Composable
private fun ReadingHeader(
    current: WeighIn?,
    previous: WeighIn?,
    status: ScaleStatus,
    settings: Settings,
    all: List<WeighIn>,
    onDetail: (WeighIn) -> Unit,
    scrollToEnd: Boolean = false,
) {
    val c = Tare.colors
    val unit = settings.unit
    val live = (status as? ScaleStatus.Weighing)?.weightKg
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).then(if (current != null && live == null) Modifier.clickable { onDetail(current) } else Modifier)) {
            val kg = live ?: current?.weightKg
            Row(verticalAlignment = Alignment.Bottom) {
                if (kg == null) {
                    Text("—", style = Tare.bigNumber, color = c.ink)
                } else {
                    val d = WeightFormat.display(kg, unit)
                    if (unit == WeightUnit.ST) {
                        val (st, lb) = d.value.split(" st ")
                        Text(st, style = Tare.bigNumber, color = c.ink)
                        Text(" st ", style = Tare.body.copy(fontSize = 22.sp), color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
                        Text(lb, style = Tare.bigNumber, color = c.ink)
                        Text(" lb", style = Tare.body.copy(fontSize = 22.sp), color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
                    } else {
                        Text(d.value, style = Tare.bigNumber, color = c.ink)
                        Text(" ${d.unit}", style = Tare.body.copy(fontSize = 24.sp), color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
                    }
                }
            }
            when {
                live != null -> Text("Hold still — measuring", style = Tare.body.copy(fontWeight = FontWeight.Medium), color = c.accent)
                current != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (previous != null) {
                            val diff = current.weightKg - previous.weightKg
                            val delta = WeightFormat.delta(diff, unit)
                            if (delta.value != "0.0") {
                                Icon(if (diff < 0) TareIcons.ArrowDown else TareIcons.ArrowUp, null, tint = c.accent, modifier = Modifier.size(20.dp))
                            }
                            Text(delta.value, style = Tare.body.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = c.accent)
                            Text(" ${delta.unit}", style = Tare.body, color = c.accent)
                            Spacer(Modifier.width(14.dp))
                        }
                        Icon(TareIcons.Clock, null, tint = c.inkSoft, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(Format.dayAndTime(current.takenAt), style = Tare.body, color = c.inkSoft, maxLines = 1)
                    }
                    if (current.source != Source.SCALE) {
                        Text(
                            if (current.source == Source.MANUAL) "Logged by hand" else "Taken offline, imported from the scale",
                            style = Tare.caption, color = c.inkFaint, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                else -> Text("No weigh-ins yet", style = Tare.body, color = c.inkSoft)
            }
        }
        GoalRing(settings, all, current)
    }
}

/** Progress from the first weigh-in towards the goal, as a ring. */
@Composable
private fun GoalRing(settings: Settings, all: List<WeighIn>, current: WeighIn?) {
    val c = Tare.colors
    val goal = settings.goalWeightKg
    val start = all.firstOrNull()?.weightKg
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        if (goal == null || start == null || current == null || abs(start - goal) < 0.05) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                Icon(
                    if (current?.source == Source.MANUAL) TareIcons.Pencil else TareIcons.Scale,
                    null, tint = c.accent, modifier = Modifier.size(34.dp),
                )
            }
        } else {
            val progress = ((start - current.weightKg) / (start - goal)).coerceIn(0.0, 1.0).toFloat()
            Canvas(Modifier.size(76.dp)) {
                val stroke = 8.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(c.accentSoft, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(c.accent, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(progress * 100).toInt()}%", style = Tare.label.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = c.ink)
                Text("of goal", style = Tare.caption.copy(fontSize = 11.sp), color = c.inkSoft)
            }
        }
    }
}

@Composable
private fun CompositionNotice(current: WeighIn?, settings: Settings, onSettings: () -> Unit) {
    val c = Tare.colors
    val text = when {
        current == null -> return
        !settings.profile.isComplete -> "Add your height and birth date to see body composition. ›"
        current.source == Source.MANUAL -> return
        current.impedanceOhms == null -> "No body composition this time: the scale needs bare feet on its electrodes."
        else -> return
    }
    Text(
        text,
        style = Tare.caption,
        color = c.inkSoft,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.tile)
            .then(if (!settings.profile.isComplete) Modifier.clickable(onClick = onSettings) else Modifier)
            .padding(12.dp),
    )
}

private fun goalSubtitle(settings: Settings, latest: WeighIn?): String {
    val goal = settings.goalWeightKg ?: return "Set a target"
    val now = latest?.weightKg ?: return WeightFormat.text(goal, settings.unit)
    val left = now - goal
    if (abs(left) < 0.1) return "Reached"
    val d = WeightFormat.delta(left, settings.unit)
    return "${d.value} ${d.unit} to go"
}
