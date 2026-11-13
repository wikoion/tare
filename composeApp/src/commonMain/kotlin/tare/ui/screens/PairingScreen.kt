package tare.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tare.AppGraph
import tare.ble.FoundScale
import tare.ble.ScaleStatus
import tare.platform.openAppSettings
import tare.ui.theme.Tare
import tare.ui.theme.TareIcons

/** Scans while shown; tapping a found scale pairs it and calls [onPaired]. */
@Composable
fun PairingContent(graph: AppGraph, onPaired: () -> Unit) {
    val c = Tare.colors
    val found by graph.scale.found.collectAsState()
    val status by graph.scale.status.collectAsState()
    DisposableEffect(Unit) {
        graph.scale.startPairing()
        onDispose { graph.scale.stopPairing() }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Pulse()
        Spacer(Modifier.height(18.dp))
        Text("Step on your scale", style = Tare.section, color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "That wakes it up. It should appear below within a few seconds. Keep the VeSync app closed while pairing.",
            style = Tare.body, color = c.inkSoft, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(20.dp))
        when (status) {
            ScaleStatus.Unauthorized -> Notice("Tare needs Bluetooth access to find your scale. Tap to open Settings.") { openAppSettings() }
            ScaleStatus.BluetoothOff -> Notice("Turn on Bluetooth to find your scale.", null)
            ScaleStatus.Unsupported -> Notice("Bluetooth isn't available on this device.", null)
            else -> Unit
        }
        found.filter { it.model != null }.forEach { scale -> FoundRow(scale) { graph.scale.pair(scale); onPaired() } }
        val others = found.filter { it.model == null }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Other devices that might be a scale. Pair one only if yours doesn't appear above.",
                style = Tare.caption, color = c.inkSoft, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
            others.forEach { scale -> FoundRow(scale) { graph.scale.pair(scale); onPaired() } }
        }
    }
}

@Composable
private fun FoundRow(scale: FoundScale, onClick: () -> Unit) {
    val c = Tare.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.tile)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(TareIcons.Scale, null, tint = c.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(scale.model?.let { "Etekcity ${it.displayName}" } ?: "Unrecognised device", style = Tare.body.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
            Text("${scale.name} · signal ${signal(scale.rssi)}", style = Tare.caption, color = c.inkSoft)
        }
        Text("Pair", style = Tare.label, color = c.accent)
    }
}

private fun signal(rssi: Int) = when {
    rssi > -60 -> "strong"
    rssi > -80 -> "good"
    else -> "weak"
}

@Composable
private fun Notice(text: String, onClick: (() -> Unit)?) {
    val c = Tare.colors
    val (bg, fg) = c.caution
    Text(
        text,
        style = Tare.body,
        color = fg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    )
}

@Composable
private fun Pulse() {
    val c = Tare.colors
    val t = rememberInfiniteTransition()
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart))
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(150.dp)) {
            for (k in 0..1) {
                val f = (p + k * 0.5f) % 1f
                drawCircle(c.accent.copy(alpha = 0.35f * (1 - f)), radius = size.minDimension / 2 * (0.45f + 0.55f * f), style = Stroke(2.dp.toPx()))
            }
        }
        Box(Modifier.size(76.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(TareIcons.Scale, null, tint = c.accent, modifier = Modifier.size(40.dp))
        }
    }
}
