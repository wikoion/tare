package tare

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController
import tare.data.DemoData
import tare.data.TareRepository
import tare.data.createSqlDriver
import tare.health.HealthBridge
import tare.platform.regionalWeightUnit
import tare.ui.App
import tare.ui.LaunchOptions
import tare.units.WeightUnit

private var graph: AppGraph? = null

/**
 * Entry point for the Swift app. [health] is the Swift HealthKit bridge. [demoData] uses a
 * separate database seeded with synthetic history (launch argument `-demoData`, for
 * screenshots); with it, `-unit kg|lb|st` and `-onboarding` adjust the demo settings.
 */
fun MainViewController(health: HealthBridge, demoData: Boolean): UIViewController {
    val args = NSProcessInfo.processInfo.arguments.map { it.toString() }
    val g = graph ?: run {
        val repo = TareRepository(createSqlDriver(if (demoData) "tare-demo.db" else "tare.db"), regionalWeightUnit())
        if (demoData) {
            DemoData.seed(repo)
            val unit = args.indexOf("-unit").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
            WeightUnit.entries.firstOrNull { it.name.equals(unit, ignoreCase = true) }?.let { u ->
                repo.updateSettings { it.copy(unit = u) }
            }
            repo.updateSettings { it.copy(onboarded = "-onboarding" !in args) }
        }
        AppGraph(repo, health, demo = demoData).also { graph = it }
    }
    val launch = LaunchOptions.parse(args)
    return ComposeUIViewController { App(g, launch) }
}
