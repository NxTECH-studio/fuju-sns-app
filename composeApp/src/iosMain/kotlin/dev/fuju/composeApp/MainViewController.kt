package dev.fuju.composeApp

import androidx.compose.ui.window.ComposeUIViewController
import dev.fuju.composeApp.di.AppContainer
import platform.UIKit.UIViewController

/**
 * iOS からホストする `UIViewController` を返すファクトリ。
 * iosApp (SwiftUI) 側では `ComposeMainViewControllerFactoryKt.MainViewController(container:)`
 * として呼び出す。
 */
fun MainViewController(container: AppContainer): UIViewController =
    ComposeUIViewController {
        ComposeAppRoot(deps = container.asAppDependencies())
    }
