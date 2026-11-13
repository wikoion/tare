package tare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tare.body.Rating
import tare.ui.theme.Tare
import tare.ui.theme.TareIcons

@Composable
fun RatingChip(rating: Rating, modifier: Modifier = Modifier) {
    val (bg, fg) = Tare.colors.chip(rating.tone)
    Text(
        rating.label,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 4.dp),
        color = fg,
        style = Tare.label,
        maxLines = 1,
    )
}

/** A metric in a soft rounded tile: label, value with unit, optional rating chip. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String,
    rating: Rating?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = Tare.colors
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(c.tile)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = c.inkSoft, style = Tare.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            val style = if (value.length + unit.length > 7) Tare.tileNumber.copy(fontSize = 23.sp) else Tare.tileNumber
            Text(value, color = c.ink, style = style, maxLines = 1, softWrap = false)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = c.inkSoft, style = Tare.caption, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        if (rating != null) RatingChip(rating) else Spacer(Modifier.height(26.dp))
    }
}

/** A translucent tile on the gradient header. */
@Composable
fun HeaderTile(icon: ImageVector, title: String, subtitle: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Tare.colors
    Row(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(c.tileOnHeader)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, style = Tare.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, color = c.inkSoft, style = Tare.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(TareIcons.Forward, null, tint = c.inkSoft, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun IconButtonSoft(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color = Tare.colors.ink) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(26.dp))
    }
}

/** Title bar for pushed screens, sitting below the status bar. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(56.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(52.dp)) {
            if (onBack != null) IconButtonSoft(TareIcons.Back, "Back", onBack)
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = Tare.title,
            color = Tare.colors.ink,
            maxLines = 1,
        )
        Row(Modifier.width(52.dp), horizontalArrangement = Arrangement.End, content = actions)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Tare.colors.card).padding(20.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = Tare.section, color = Tare.colors.ink)
}

/** A full-width soft button, the app's main call to action style. */
@Composable
fun SoftButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Tare.colors
    val bg = when {
        !enabled -> c.tile
        filled -> c.accent
        else -> c.tile
    }
    val fg = when {
        !enabled -> c.inkFaint
        filled -> if (c.dark) Color(0xFF00201C) else Color.White
        else -> c.ink
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 17.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = fg, style = Tare.body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
    }
}
