plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// The shared app: Compose UI, Bluetooth (Kable) and Apple Health glue. iOS only for now; an
// Android target needs only `androidTarget()` here plus Android implementations of the
// `expect` declarations in tare/platform.
kotlin {
    jvmToolchain(21)

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            linkerOpts("-lsqlite3")
        }
    }

    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        languageSettings.optIn("androidx.compose.material3.ExperimentalMaterial3Api")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kable)
        }
    }
}
