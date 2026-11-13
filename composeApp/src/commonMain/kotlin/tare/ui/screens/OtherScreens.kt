package tare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tare.AppGraph
import tare.platform.appVersion
import tare.platform.shareFile
import tare.ui.components.SoftButton
import tare.ui.components.TopBar
import tare.ui.theme.Tare
import tare.ui.theme.TareIcons

@Composable
fun PairingScreen(graph: AppGraph, onBack: () -> Unit) {
    val c = Tare.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.background(c.headerBrush)) { TopBar("Pair a scale", onBack) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            PairingContent(graph, onPaired = onBack)
        }
    }
}

@Composable
fun LogScreen(graph: AppGraph, onBack: () -> Unit) {
    val c = Tare.colors
    val log by graph.scale.log.collectAsState()
    Column(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.background(c.headerBrush)) {
            TopBar("Bluetooth log", onBack) {
                tare.ui.components.IconButtonSoft(TareIcons.Share, "Share log", onClick = {
                    shareFile("tare-bluetooth-log.txt", log.joinToString("\n"))
                })
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                "Devices heard while pairing and raw frames from this session. If your scale misbehaves or doesn't appear, sharing this in a GitHub issue helps support it.",
                style = Tare.caption, color = c.inkSoft,
            )
            Spacer(Modifier.height(10.dp))
            SelectionContainer {
                Text(
                    if (log.isEmpty()) "Nothing yet." else log.joinToString("\n"),
                    style = Tare.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = c.ink,
                )
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val c = Tare.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.background(c.headerBrush)) { TopBar("About", onBack) }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Group("Tare $appVersion") {
                Para("An open-source companion app for the Etekcity ESF-24 and ESF-551 smart fitness scales (and the ESF-17 and ESF-18, which work like the ESF-24). Everything stays on your phone: no account, no cloud.")
                Para("Released under the MIT licence.")
            }
            Group("Safety") {
                Para("The scale measures body composition by passing a small electrical current through your body. Don't use the body-composition features if you have a pacemaker or other implanted medical device, or if you might be pregnant. This app can't switch on the vendor's zero-current or pregnancy modes.")
                Para("Body-composition values are bioimpedance estimates, not clinical measurements. Tare is not a medical device; don't use it for medical decisions.")
            }
            Group("Credits") {
                Para("The scale's Bluetooth protocol and the body-composition formulas were reverse-engineered by the etekcity_esf551_ble project (MIT licence, © Ron Ben Yosef) and community contributors. Tare reimplements them in Kotlin.")
                Para("Built with Kotlin Multiplatform, Compose Multiplatform, Kable (Apache 2.0) and SQLDelight (Apache 2.0).")
            }
            Group("Trademarks") {
                Para("Tare is an independent project. It is not affiliated with, endorsed by or connected to Etekcity or VeSync Co., Ltd. \"Etekcity\" and \"VeSync\" are trademarks of their respective owners and are used here only to say which scale this app works with.")
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun Para(text: String) = Text(text, style = Tare.body, color = Tare.colors.inkSoft)

/** First run: welcome, profile, pairing. Every step can be skipped. */
@Composable
fun OnboardingScreen(graph: AppGraph) {
    val c = Tare.colors
    var step by remember { mutableIntStateOf(0) }
    val settings by graph.repo.settings.collectAsState()

    Column(
        Modifier.fillMaxSize().background(c.headerBrush).windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (step) {
                0 -> {
                    Spacer(Modifier.height(48.dp))
                    Box(Modifier.size(112.dp).clip(CircleShape).background(c.tileOnHeader), contentAlignment = Alignment.Center) {
                        Icon(TareIcons.Scale, null, tint = c.accent, modifier = Modifier.size(60.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Tare", style = Tare.bigNumber, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "An open-source app for Etekcity smart scales. Your weigh-ins stay on this phone.",
                        style = Tare.body.copy(fontSize = 18.sp), color = c.inkSoft, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp)).background(c.card).padding(18.dp)) {
                        Text("Before you start", style = Tare.body.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The scale sends a small current through your body to estimate body composition. Don't use those features with a pacemaker or other implant, or if you might be pregnant. Results are estimates, not medical measurements.",
                            style = Tare.caption, color = c.inkSoft,
                        )
                    }
                }
                1 -> {
                    Text("About you", style = Tare.title.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold), color = c.ink, modifier = Modifier.fillMaxWidth())
                    Text("Used to estimate body composition. Stored only on this phone.", style = Tare.body, color = c.inkSoft, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp)).background(c.card).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) { ProfileEditor(graph, settings) }
                }
                else -> {
                    Text("Pair your scale", style = Tare.title.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold), color = c.ink, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                    PairingContent(graph, onPaired = { graph.finishOnboarding() })
                }
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp).windowInsetsPadding(WindowInsets.navigationBars)) {
            when (step) {
                0 -> SoftButton("Get started", null, filled = true, onClick = { step = 1 })
                1 -> SoftButton(
                    if (settings.profile.isComplete) "Continue" else "Skip for now",
                    null, filled = settings.profile.isComplete, onClick = { step = 2 },
                )
                else -> SoftButton("Skip for now", null, onClick = { graph.finishOnboarding() })
            }
        }
    }
}
