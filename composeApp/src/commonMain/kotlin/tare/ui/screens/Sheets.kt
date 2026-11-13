package tare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import tare.data.Settings
import tare.data.Source
import tare.data.WeighIn
import tare.data.composition
import tare.ui.Format
import tare.ui.Metric
import tare.ui.components.RatingChip
import tare.ui.components.SoftButton
import tare.ui.of
import tare.ui.theme.Tare
import tare.ui.theme.TareIcons
import tare.units.WeightFormat
import tare.units.WeightUnit
import tare.units.formatDecimal
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun TareSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        containerColor = Tare.colors.card,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            content = content,
        )
    }
}

private fun unitHint(unit: WeightUnit) = when (unit) {
    WeightUnit.KG -> "kg"
    WeightUnit.LB -> "lb"
    WeightUnit.ST -> "st lb, e.g. 12 5.1"
}

@Composable
fun LogWeightSheet(unit: WeightUnit, onSave: (Instant, Double) -> Unit, onDismiss: () -> Unit) {
    val c = Tare.colors
    var text by remember { mutableStateOf("") }
    val zone = TimeZone.currentSystemDefault()
    val now = remember { Clock.System.now().toLocalDateTime(zone) }
    var date by remember { mutableStateOf(now.date) }
    var picking by remember { mutableStateOf(false) }
    val kg = WeightFormat.parse(text, unit)

    TareSheet(onDismiss) {
        Text("Log weight", style = Tare.title, color = c.ink)
        Text("For weigh-ins on another scale. Manual entries record weight only.", style = Tare.caption, color = c.inkSoft)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Weight (${unitHint(unit)})") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (unit == WeightUnit.ST) KeyboardType.Text else KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        SettingRow("Date", if (date == now.date) "Today" else Format.shortDate(date) + " ${date.year}") { picking = true }
        Spacer(Modifier.height(18.dp))
        SoftButton("Save", TareIcons.Check, filled = true, enabled = kg != null, onClick = {
            val at = if (date == now.date) Clock.System.now() else LocalDateTime(date, now.time).toInstant(zone)
            onSave(at, kg!!)
        })
    }
    if (picking) {
        DateDialog(date, latest = now.date, onPick = { date = it; picking = false }, onDismiss = { picking = false })
    }
}

@Composable
fun GoalSheet(settings: Settings, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    val c = Tare.colors
    val unit = settings.unit
    var text by remember {
        mutableStateOf(settings.goalWeightKg?.let { WeightFormat.display(it, unit).value.replace(" st ", " ") } ?: "")
    }
    val kg = WeightFormat.parse(text, unit)
    TareSheet(onDismiss) {
        Text("Goal weight", style = Tare.title, color = c.ink)
        Text("Progress is measured from your first weigh-in.", style = Tare.caption, color = c.inkSoft)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Goal (${unitHint(unit)})") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (unit == WeightUnit.ST) KeyboardType.Text else KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        settings.profile.heightCm?.let { h ->
            val range = tare.body.Ratings.healthyWeightRangeKg(h)
            Spacer(Modifier.height(8.dp))
            Text(
                "A healthy BMI at your height is ${WeightFormat.text(range.start, unit)} to ${WeightFormat.text(range.endInclusive, unit)}.",
                style = Tare.caption, color = c.inkSoft,
            )
        }
        Spacer(Modifier.height(18.dp))
        SoftButton("Save goal", TareIcons.Flag, filled = true, enabled = kg != null, onClick = { onSave(kg) })
        if (settings.goalWeightKg != null) {
            Spacer(Modifier.height(10.dp))
            SoftButton("Remove goal", null, onClick = { onSave(null) })
        }
    }
}

@Composable
fun WeighInSheet(weighIn: WeighIn, settings: Settings, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val c = Tare.colors
    var confirming by remember { mutableStateOf(false) }
    val comp = weighIn.composition(settings.profile)
    TareSheet(onDismiss) {
        val d = WeightFormat.display(weighIn.weightKg, settings.unit)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(d.value, style = Tare.bigNumber.copy(fontSize = 44.sp), color = c.ink)
            Text(" ${d.unit}", style = Tare.body.copy(fontSize = 20.sp), color = c.ink, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            Format.dayAndTime(weighIn.takenAt) + " · " + when (weighIn.source) {
                Source.SCALE -> "scale"
                Source.SCALE_OFFLINE -> "scale, offline"
                Source.MANUAL -> "logged by hand"
            },
            style = Tare.body, color = c.inkSoft,
        )
        Spacer(Modifier.height(14.dp))
        Metric.entries.drop(1).forEach { m ->
            val v = m.of(weighIn, settings.profile, settings.unit, comp) ?: return@forEach
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.label, style = Tare.body, color = c.inkSoft, modifier = Modifier.weight(1f))
                v.rating?.let { RatingChip(it); Spacer(Modifier.width(10.dp)) }
                Text(v.text, style = Tare.body.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                if (v.unit.isNotEmpty()) Text(" ${v.unit}", style = Tare.caption, color = c.inkSoft)
            }
        }
        if (weighIn.impedanceOhms != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Impedance: ${weighIn.impedanceOhms} Ω at 50 kHz" +
                    (weighIn.impedanceHighOhms?.let { ", $it Ω at 500 kHz" } ?: ""),
                style = Tare.caption, color = c.inkFaint,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (confirming) "Tap again to delete" else "Delete weigh-in",
            style = Tare.body.copy(fontWeight = FontWeight.SemiBold),
            color = c.alert.second,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.alert.first)
                .clickable { if (confirming) onDelete() else confirming = true }
                .padding(16.dp),
        )
        if (weighIn.healthSynced) {
            Text("Also removes it from Apple Health.", style = Tare.caption, color = c.inkFaint, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun HeightSheet(heightCm: Double?, imperial: Boolean, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    val c = Tare.colors
    val totalIn = heightCm?.let { kotlin.math.round(it / 2.54).toInt() }
    var cm by remember { mutableStateOf(heightCm?.let { formatDecimal(it, 0) } ?: "") }
    var ft by remember { mutableStateOf(totalIn?.let { (it / 12).toString() } ?: "") }
    var inch by remember { mutableStateOf(totalIn?.let { (it % 12).toString() } ?: "") }
    val parsed: Double? = if (imperial) {
        val f = ft.toIntOrNull()
        val i = inch.ifBlank { "0" }.toDoubleOrNull()
        if (f != null && i != null) (f * 12 + i) * 2.54 else null
    } else {
        cm.replace(',', '.').toDoubleOrNull()
    }?.takeIf { it in 100.0..230.0 }

    TareSheet(onDismiss) {
        Text("Height", style = Tare.title, color = c.ink)
        Spacer(Modifier.height(16.dp))
        if (imperial) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(ft, { ft = it }, label = { Text("Feet") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(inch, { inch = it }, label = { Text("Inches") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        } else {
            OutlinedTextField(cm, { cm = it }, label = { Text("Centimetres") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        Spacer(Modifier.height(18.dp))
        SoftButton("Save", TareIcons.Check, filled = true, enabled = parsed != null, onClick = { onSave(parsed!!) })
    }
}

/** A Material date picker for a local date; [latest] caps the selectable range. */
@Composable
fun DateDialog(initial: LocalDate?, latest: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit, earliestYear: Int = latest.year - 5) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
        yearRange = earliestYear..latest.year,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    val picked = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
                    onPick(if (picked > latest) latest else picked)
                } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state)
    }
}

/** A tappable label/value row with a chevron. */
@Composable
fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    val c = Tare.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.tile)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Tare.body, color = c.ink, modifier = Modifier.weight(1f))
        Text(value, style = Tare.body, color = c.inkSoft)
        Spacer(Modifier.width(6.dp))
        androidx.compose.material3.Icon(TareIcons.Forward, null, tint = c.inkFaint, modifier = Modifier.padding(start = 2.dp).width(16.dp))
    }
}
