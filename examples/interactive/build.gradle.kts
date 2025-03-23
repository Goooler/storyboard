import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

group = "dev.bnorm.storyboard.example"
version = "0.1-SNAPSHOT"

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.executable()
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":storyboard-core"))
                implementation(project(":storyboard-easel"))
                implementation(project(":storyboard-text"))

                implementation(project(":examples:shared"))

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.components.resources)
            }
        }
        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose {
    resources.publicResClass = true
    desktop.application.mainClass = "Main_desktopKt"
}

tasks.register<Sync>("site") {
    from(tasks.named("wasmJsBrowserDistribution"))
    into(rootProject.layout.buildDirectory.dir("_site/example/${project.name}"))
}
