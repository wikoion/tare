package tare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tare.AppGraph
import tare.ble.ScaleStatus
import tare.body.Sex
import tare.data.CsvExport
import tare.data.Settings
import tare.platform.appVersion
import tare.platform.shareFile
import tare.protocol.ScalePlatform
import tare.ui.Format
import tare.ui.components.SoftButton
import tare.ui.components.TopBar
import tare.ui.theme.Tare
import tare.ui.theme.TareIcons
import tare.units.HeightFormat
import tare.units.WeightFormat
import tare.units.WeightUnit
import kotlin.time.Clock

@Composable
fun SettingsScreen(graph: AppGraph, onBack: () -> Unit, onPair: () -> Unit, onGoal: () -> Unit, onLog: () -> Unit, onAbout: () -> Unit) {
    val c = Tare.colors
    val settings by graph.repo.settings.collectAsState()
    val status by graph.scale.status.collectAsState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.background(c.headerBrush)) { TopBar("Settings", onBack) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Group("Profile", "Body composition estimates depend on these.") {
                ProfileEditor(graph, settings)
                ToggleRow(
                    "Athlete mode",
                    "For people who train hard most days. Lowers the body-fat estimate.",
                    settings.profile.athlete,
                ) { on -> graph.repo.updateSettings { it.copy(profile = it.profile.copy(athlete = on)) } }
            }

            Group("Goal") {
                SettingRow("Goal weight", settings.goalWeightKg?.let { WeightFormat.text(it, settings.unit) } ?: "Not set", onGoal)
            }

            Group("Scale") {
                val paired = settings.pairedScale
                if (paired == null) {
                    Text("No scale paired.", style = Tare.body, color = c.inkSoft)
                    SoftButton("Pair a scale", TareIcons.Bluetooth, filled = true, onClick = onPair)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Etekcity ${paired.name}", style = Tare.body.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                            Text(statusText(status), style = Tare.caption, color = c.inkSoft)
                        }
                        Text(
                            "Forget",
                            style = Tare.label,
                            color = c.alert.second,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { graph.scale.forget() }.padding(10.dp),
                        )
                    }
                }
                // Only QN scales (the ESF-24 family) keep weigh-ins taken while nothing was connected.
                if (settings.pairedScale?.model?.platform != ScalePlatform.ETEKCITY) {
                    ToggleRow(
                        "Fetch offline weigh-ins",
                        "Imports weigh-ins the scale stored while this app wasn't connected. The scale deletes them as it sends them, so the VeSync app won't see them.",
                        settings.importOfflineReadings,
                    ) { on -> graph.repo.updateSettings { it.copy(importOfflineReadings = on) } }
                }
                SettingRow("Bluetooth log", "", onLog)
            }

            if (graph.health.isAvailable) {
                Group("Apple Health") {
                    ToggleRow(
                        "Save to Apple Health",
                        "Writes weight, BMI, body fat and lean body mass for every weigh-in, including past ones.",
                        settings.appleHealth,
                    ) { on ->
                        if (!on) {
                            graph.repo.updateSettings { it.copy(appleHealth = false) }
                        } else {
                            scope.launch {
                                graph.health.authorize()
                                graph.repo.updateSettings { it.copy(appleHealth = true) }
                                graph.health.syncPending()
                            }
                        }
                    }
                }
            }

            Group("Data") {
                SettingRow("Export CSV", "") {
                    shareFile("tare-weigh-ins.csv", CsvExport.build(graph.repo.all(), settings.profile))
                }
                SettingRow("About Tare", appVersion, onAbout)
            }
            Spacer(Modifier.height(12.dp).windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** Sex, birth date, height and units: shared by Settings and onboarding. */
@Composable
fun ProfileEditor(graph: AppGraph, settings: Settings) {
    val c = Tare.colors
    var editingHeight by remember { mutableStateOf(false) }
    var editingBirth by remember { mutableStateOf(false) }
    val profile = settings.profile
    val imperial = settings.unit != WeightUnit.KG

    Segmented(
        listOf("Male", "Female"),
        if (profile.sex == Sex.MALE) 0 else 1,
    ) { i -> graph.repo.updateSettings { it.copy(profile = it.profile.copy(sex = if (i == 0) Sex.MALE else Sex.FEMALE)) } }
    SettingRow(
        "Birth date",
        profile.birthDate?.let { Format.shortDate(it) + " ${it.year}" } ?: "Add",
    ) { editingBirth = true }
    SettingRow("Height", profile.heightCm?.let { HeightFormat.text(it, imperial) } ?: "Add") { editingHeight = true }
    Text("Units", style = Tare.label, color = c.inkSoft, modifier = Modifier.padding(top = 4.dp))
    Segmented(listOf("kg", "lb", "st"), settings.unit.ordinal) { i ->
        graph.repo.updateSettings { it.copy(unit = WeightUnit.entries[i]) }
    }

    if (editingHeight) {
        HeightSheet(profile.heightCm, imperial, onSave = { cm ->
            graph.repo.updateSettings { it.copy(profile = it.profile.copy(heightCm = cm)) }
            editingHeight = false
        }, onDismiss = { editingHeight = false })
    }
    if (editingBirth) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        DateDialog(
            initial = profile.birthDate ?: kotlinx.datetime.LocalDate(today.year - 35, 1, 1),
            latest = today,
            earliestYear = today.year - 100,
            onPick = { d ->
                graph.repo.updateSettings { it.copy(profile = it.profile.copy(birthDate = d)) }
                editingBirth = false
            },
            onDismiss = { editingBirth = false },
        )
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Tare.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.tile).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            Text(
                label,
                style = Tare.body.copy(fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (i == selected) c.ink else c.inkSoft,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (i == selected) c.card else c.tile)
                    .clickable { onSelect(i) }
                    .padding(vertical = 11.dp),
            )
        }
    }
}

@Composable
fun ToggleRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Tare.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = Tare.body, color = c.ink)
            if (description != null) Text(description, style = Tare.caption, color = c.inkSoft)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, uncheckedTrackColor = c.tile, uncheckedBorderColor = c.divider),
        )
    }
}

@Composable
fun Group(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Tare.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Text(title, style = Tare.section, color = c.ink)
            if (subtitle != null) Text(subtitle, style = Tare.caption, color = c.inkSoft)
        }
        content()
    }
}

fun statusText(status: ScaleStatus): String = when (status) {
    ScaleStatus.Starting -> "Starting…"
    ScaleStatus.Unsupported -> "Bluetooth isn't available on this device"
    ScaleStatus.BluetoothOff -> "Bluetooth is off"
    ScaleStatus.Unauthorized -> "Bluetooth access not allowed"
    ScaleStatus.NotPaired -> "Not paired"
    ScaleStatus.Pairing -> "Searching…"
    ScaleStatus.Waiting -> "Ready: step on the scale to weigh"
    ScaleStatus.Connected -> "Connected"
    is ScaleStatus.Weighing -> "Weighing…"
}
