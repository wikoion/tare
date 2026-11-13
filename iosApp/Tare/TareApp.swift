import SwiftUI
import UIKit
import ComposeApp

@main
struct TareApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
        }
    }
}

/// Hosts the shared Compose UI. Everything except Apple Health lives in Kotlin.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let demo = ProcessInfo.processInfo.arguments.contains("-demoData")
        return MainViewControllerKt.MainViewController(health: HealthKitBridge.shared, demoData: demo)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
