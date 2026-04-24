import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            // Link as static framework into SwiftUI iosApp via XCFramework.
            isStatic = (project.findProperty("fuju.iosFramework.isStatic") as? String)?.toBoolean() ?: true
            export(project(":core:domain"))
            export(project(":core:error"))
            export(project(":feature:auth:domain"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:error"))
            implementation(project(":core:network"))
            implementation(project(":core:storage"))
            implementation(project(":core:ui"))

            implementation(project(":feature:auth:data"))
            implementation(project(":feature:auth:domain"))
            implementation(project(":feature:auth:ui"))

            implementation(project(":feature:timeline:data"))
            implementation(project(":feature:timeline:domain"))
            implementation(project(":feature:timeline:ui"))

            implementation(project(":feature:profile:data"))
            implementation(project(":feature:profile:domain"))
            implementation(project(":feature:profile:ui"))

            implementation(project(":feature:admin:data"))
            implementation(project(":feature:admin:domain"))
            implementation(project(":feature:admin:ui"))

            api(project(":feature:auth:domain"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

android {
    namespace = "dev.fuju.composeApp"
    compileSdk =
        libs.versions.android.compile.sdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.min.sdk
                .get()
                .toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
