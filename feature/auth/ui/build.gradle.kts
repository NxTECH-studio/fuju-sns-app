plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.roborazzi)
}

kotlin {
    jvmToolchain(17)
    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:error"))
            implementation(project(":core:ui"))
            implementation(project(":feature:auth:domain"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Roborazzi + Robolectric は Android SDK に依存するので androidUnitTest 限定で依存を入れる。
        // commonTest には入れられない（KMP の iOS ターゲットで解決できないため）。
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.rule)
            implementation(libs.androidx.test.ext.junit)
            implementation(compose.uiTest)
            implementation(compose.material3)
        }
    }
}

android {
    namespace = "dev.fuju.feature.auth.ui"
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
    // Robolectric は resources / assets に依存するため UnitTest で Android resources を
    // 読めるようにする。roborazzi もここを経由して Compose の drawable を解決する。
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}
