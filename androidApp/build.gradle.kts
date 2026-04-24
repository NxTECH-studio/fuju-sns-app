import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// `local.properties` から開発者固有の URL を読み込む。
// キー名は `fuju.apiBaseUrl` / `fuju.authCoreBaseUrl` / `fuju.socialRedirectUri`。
// 欠けている場合は flavor の既定値にフォールバックする。
// 詳細は `.env.example` を参照。
val localProperties: Properties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            FileInputStream(file).use { stream -> load(stream) }
        }
    }

fun localOrDefault(
    key: String,
    default: String,
): String = localProperties.getProperty(key)?.takeIf { it.isNotBlank() } ?: default

android {
    namespace = "dev.fuju.app"
    compileSdk =
        libs.versions.android.compile.sdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "dev.fuju.app"
        minSdk =
            libs.versions.android.min.sdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.target.sdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            val apiBaseUrl = localOrDefault("fuju.apiBaseUrl", "http://10.0.2.2:8080")
            val authCoreBaseUrl = localOrDefault("fuju.authCoreBaseUrl", "http://10.0.2.2:8081")
            val socialRedirectUri = localOrDefault("fuju.socialRedirectUri", "fuju://auth/callback")
            buildConfigField("String", "FUJU_API_BASE_URL", "\"$apiBaseUrl\"")
            buildConfigField("String", "AUTH_CORE_BASE_URL", "\"$authCoreBaseUrl\"")
            buildConfigField("String", "SOCIAL_REDIRECT_URI", "\"$socialRedirectUri\"")
        }
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            val apiBaseUrl = localOrDefault("fuju.apiBaseUrl", "https://api-staging.fuju.example.com")
            val authCoreBaseUrl = localOrDefault("fuju.authCoreBaseUrl", "https://auth-staging.fuju.example.com")
            val socialRedirectUri = localOrDefault("fuju.socialRedirectUri", "fuju://auth/callback")
            buildConfigField("String", "FUJU_API_BASE_URL", "\"$apiBaseUrl\"")
            buildConfigField("String", "AUTH_CORE_BASE_URL", "\"$authCoreBaseUrl\"")
            buildConfigField("String", "SOCIAL_REDIRECT_URI", "\"$socialRedirectUri\"")
        }
        create("prod") {
            dimension = "env"
            val apiBaseUrl = localOrDefault("fuju.apiBaseUrl", "https://api.fuju.example.com")
            val authCoreBaseUrl = localOrDefault("fuju.authCoreBaseUrl", "https://auth.fuju.example.com")
            val socialRedirectUri = localOrDefault("fuju.socialRedirectUri", "fuju://auth/callback")
            buildConfigField("String", "FUJU_API_BASE_URL", "\"$apiBaseUrl\"")
            buildConfigField("String", "AUTH_CORE_BASE_URL", "\"$authCoreBaseUrl\"")
            buildConfigField("String", "SOCIAL_REDIRECT_URI", "\"$socialRedirectUri\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            // 仮: 正式な署名 key / R8 ルールはリリース前に設定する
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":composeApp"))
    implementation(project(":core:error"))
    implementation(project(":feature:auth:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
}
