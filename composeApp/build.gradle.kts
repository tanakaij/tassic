import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
            // Tests run in headless Chrome. The pure logic under test —
            // the capture parser, the ICS reader, habit streaks, day
            // planning — has no UI, but it does read the device clock and
            // localStorage through the js() bridge, so it needs a real
            // browser rather than a JVM harness.
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        // The canonical relational schema (src/commonMain/sqldelight/tassic/db/*.sq) is
        // compiled into typesafe query code so the schema is verified at build time.
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
        }

        wasmJsMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.browser)
        }

        // The analytics and parsing layers are pure functions over plain data,
        // which makes them both cheap to test and expensive to get subtly
        // wrong — exactly the combination that earns a test source set. The
        // capture parser shipped with a real bug (punctuation stripping ate
        // the "!" off "!high", silently disabling the whole priority grammar)
        // that a single assertion would have caught.
        wasmJsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

sqldelight {
    databases {
        create("TassicDatabase") {
            packageName.set("tassic.db")
        }
    }
}
