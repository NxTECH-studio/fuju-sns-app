import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

// ルートでは全サブプロジェクトに lint / format を強制する。
// 設定値は :core / :feature 配下でも一貫させるため allprojects で束ねる。
allprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
    apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
        android.set(false)
        ignoreFailures.set(false)
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        }
        filter {
            exclude { element -> element.file.path.contains("build/") }
            exclude { element -> element.file.path.contains("generated/") }
        }
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        ignoreFailures = false
        config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
        parallel = true
    }

    // Compose Rules (ktlint & detekt) を注入する
    dependencies {
        add("ktlintRuleset", rootProject.libs.ktlint.compose)
        add("detektPlugins", rootProject.libs.detekt.compose)
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**", "**/generated/**")
            ktlint("1.5.0").setEditorConfigPath("${rootProject.projectDir}/.editorconfig")
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint("1.5.0")
        }
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
        }
    }
}

// ルート check: lint / detekt / spotless をまとめて叩ける entry-point
tasks.register("lintAll") {
    group = "verification"
    description = "Run ktlint + detekt + spotless across all modules."
    dependsOn(
        subprojects.map { it.tasks.named("ktlintCheck") },
        subprojects.map { it.tasks.named("detekt") },
        subprojects.map { it.tasks.named("spotlessCheck") },
    )
}

tasks.register<Delete>("cleanAll") {
    group = "build"
    description = "Delete all module build directories."
    delete(rootProject.layout.buildDirectory)
    subprojects.forEach { delete(it.layout.buildDirectory) }
}
