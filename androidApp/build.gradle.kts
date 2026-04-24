plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

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
            buildConfigField("String", "FUJU_API_BASE_URL", "\"http://10.0.2.2:8080\"")
            buildConfigField("String", "AUTH_CORE_BASE_URL", "\"http://10.0.2.2:8081\"")
            buildConfigField("String", "SOCIAL_REDIRECT_URI", "\"fuju://auth/callback\"")
        }
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "FUJU_API_BASE_URL", "\"https://api-staging.fuju.example.com\"")
            buildConfigField("String", "AUTH_CORE_BASE_URL", "\"https://auth-staging.fuju.example.com\"")
            buildConfigField("String", "SOCIAL_REDIRECT_URI", "\"fuju://auth/callback\"")
        }
        create("prod") {
            dimension = "env"
            buildConfigField("String", "FUJU_API_BASE_URL", "\"https://api.fuju.example.com\"")
            buildConfigField("String", "AUTH_CORE_BASE_URL", "\"https://auth.fuju.example.com\"")
            buildConfigField("String", "SOCIAL_REDIRECT_URI", "\"fuju://auth/callback\"")
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
