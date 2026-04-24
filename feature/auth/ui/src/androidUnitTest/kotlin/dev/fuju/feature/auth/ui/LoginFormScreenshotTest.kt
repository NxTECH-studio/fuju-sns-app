package dev.fuju.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.fuju.core.ui.theme.FujuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `LoginForm` の screenshot baseline。
 *
 * - initial: フィールド空、エラーなし、providers 3 種表示
 * - providers なし: `providers = emptyList()` のシンプルなログイン
 *
 * 実際の PNG baseline 生成は Android SDK を持つ環境（CI / 開発者マシン）で
 * `./gradlew :feature:auth:ui:recordRoborazziDebug` を実行する。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [SCREENSHOT_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoginFormScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val roborazziRule =
        RoborazziRule(
            composeRule = composeTestRule,
            captureRoot = composeTestRule.onRoot(),
            options =
                RoborazziRule.Options(
                    outputDirectoryPath = "src/androidUnitTest/roborazzi",
                ),
        )

    @Test
    fun loginForm_initial() {
        composeTestRule.setContent {
            ScreenshotScaffold {
                LoginForm(
                    onLogin = TestFactories.successfulLogin,
                    onLoginWithSocial = {},
                    providers = TestFactories.defaultProviders,
                    onRegisterClick = {},
                )
            }
        }
        composeTestRule
            .onRoot()
            .captureRoboImage(filePath = "src/androidUnitTest/roborazzi/LoginForm_initial.png")
    }

    @Test
    fun loginForm_withoutProviders() {
        composeTestRule.setContent {
            ScreenshotScaffold {
                LoginForm(
                    onLogin = TestFactories.successfulLogin,
                    onLoginWithSocial = {},
                    providers = emptyList(),
                    onRegisterClick = null,
                )
            }
        }
        composeTestRule
            .onRoot()
            .captureRoboImage(filePath = "src/androidUnitTest/roborazzi/LoginForm_withoutProviders.png")
    }
}

/**
 * 各 screenshot test を同じ条件で描画するための最小スキャフォールド。
 * `FujuTheme` + surface 背景で包むことで theme 差分だけを検出対象にする。
 */
@Composable
private fun ScreenshotScaffold(content: @Composable () -> Unit) {
    FujuTheme(darkTheme = false) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            // background を明示することで Compose の draw tree が透過で終わらないようにする。
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
                content = content,
            )
        }
    }
}

/** Robolectric で使う Android SDK level。 */
private const val SCREENSHOT_SDK: Int = 34
