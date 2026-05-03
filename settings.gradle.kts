rootProject.name = "fuju-app"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    // Gradle 9 系では Foojay Toolchain resolver が必須
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

// App shell / UI entry
include(":composeApp")
include(":androidApp")

// Core layer
include(":core:domain")
include(":core:error")
include(":core:network")
include(":core:storage")
include(":core:ui")

// Feature layer
include(":feature:auth:data")
include(":feature:auth:domain")
include(":feature:auth:ui")
include(":feature:timeline:data")
include(":feature:timeline:domain")
include(":feature:timeline:ui")
include(":feature:profile:data")
include(":feature:profile:domain")
include(":feature:profile:ui")
include(":feature:admin:data")
include(":feature:admin:domain")
include(":feature:admin:ui")
