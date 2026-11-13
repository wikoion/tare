plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
}

// Platform-independent core: the scale protocol, body composition and storage.
// The JVM target exists so `./gradlew :core:jvmTest` runs the tests in seconds.
kotlin {
    jvmToolchain(21)
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.sqldelight.coroutines)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.jvm)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("TareDb") {
            packageName.set("tare.db")
        }
    }
}
