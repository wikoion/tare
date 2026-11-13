package tare.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tare.AppGraph
import tare.data.WeighIn
import tare.ui.screens.AboutScreen
import tare.ui.screens.GoalSheet
import tare.ui.screens.HomeScreen
import tare.ui.screens.LogScreen
import tare.ui.screens.LogWeightSheet
import tare.ui.screens.OnboardingScreen
import tare.ui.screens.PairingScreen
import tare.ui.screens.SettingsScreen
import tare.ui.screens.TrendsScreen
import tare.ui.screens.WeighInSheet
import tare.ui.theme.TareTheme

private sealed interface Screen {
    data object Home : Screen
    data class Trends(val metric: Metric) : Screen
    data object Settings : Screen
    data object Pairing : Screen
    data object Log : Screen
    data object About : Screen
}

private sealed interface Sheet {
    data object LogWeight : Sheet
    data object Goal : Sheet
    data class Detail(val weighIn: WeighIn) : Sheet
}

/**
 * Debug launch options, for screenshots and UI checks on the simulator:
 * `-screen trends|settings|about|log|pairing`, `-sheet detail|goal|log`, `-scrollHome`.
 */
data class LaunchOptions(val screen: String? = null, val sheet: String? = null, val scrollHome: Boolean = false) {
    companion object {
        fun parse(args: List<String>): LaunchOptions {
            fun value(flag: String) = args.indexOf(flag).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
            return LaunchOptions(value("-screen"), value("-sheet"), "-scrollHome" in args)
        }
    }
}

@Composable
fun App(graph: AppGraph, launch: LaunchOptions = LaunchOptions()) {
    TareTheme {
        val settings by graph.repo.settings.collectAsState()
        if (!settings.onboarded) {
            OnboardingScreen(graph)
            return@TareTheme
        }

        val stack = remember {
            mutableStateListOf<Screen>(Screen.Home).apply {
                when (launch.screen) {
                    "trends" -> add(Screen.Trends(Metric.WEIGHT))
                    "settings" -> add(Screen.Settings)
                    "about" -> add(Screen.About)
                    "log" -> add(Screen.Log)
                    "pairing" -> add(Screen.Pairing)
                }
            }
        }
        var sheet by remember {
            mutableStateOf<Sheet?>(
                when (launch.sheet) {
                    "detail" -> graph.repo.all().lastOrNull()?.let { Sheet.Detail(it) }
                    "goal" -> Sheet.Goal
                    "log" -> Sheet.LogWeight
                    else -> null
                },
            )
        }
        fun push(s: Screen) = stack.add(s)
        fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

        AnimatedContent(
            targetState = stack.toList(),
            transitionSpec = {
                val forward = targetState.size >= initialState.size
                if (forward) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
                } else {
                    fadeIn() togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            contentKey = { it.size to it.last()::class },
        ) { screens ->
            when (val top = screens.last()) {
                Screen.Home -> HomeScreen(
                    graph,
                    onTrends = { push(Screen.Trends(it)) },
                    onSettings = { push(Screen.Settings) },
                    onPair = { push(Screen.Pairing) },
                    onGoal = { sheet = Sheet.Goal },
                    onLog = { sheet = Sheet.LogWeight },
                    onDetail = { sheet = Sheet.Detail(it) },
                    scrollToEnd = launch.scrollHome,
                )
                is Screen.Trends -> TrendsScreen(graph, top.metric, onBack = ::pop, onDetail = { sheet = Sheet.Detail(it) })
                Screen.Settings -> SettingsScreen(
                    graph,
                    onBack = ::pop,
                    onPair = { push(Screen.Pairing) },
                    onGoal = { sheet = Sheet.Goal },
                    onLog = { push(Screen.Log) },
                    onAbout = { push(Screen.About) },
                )
                Screen.Pairing -> PairingScreen(graph, onBack = ::pop)
                Screen.Log -> LogScreen(graph, onBack = ::pop)
                Screen.About -> AboutScreen(onBack = ::pop)
            }
        }

        when (val s = sheet) {
            null -> Unit
            Sheet.LogWeight -> LogWeightSheet(
                settings.unit,
                onSave = { at, kg -> graph.addManual(at, kg); sheet = null },
                onDismiss = { sheet = null },
            )
            Sheet.Goal -> GoalSheet(
                settings,
                onSave = { kg -> graph.repo.updateSettings { it.copy(goalWeightKg = kg) }; sheet = null },
                onDismiss = { sheet = null },
            )
            is Sheet.Detail -> WeighInSheet(
                s.weighIn,
                settings,
                onDelete = { graph.delete(s.weighIn); sheet = null },
                onDismiss = { sheet = null },
            )
        }
    }
}
