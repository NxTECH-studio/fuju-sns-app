package dev.fuju.feature.timeline.ui

import androidx.compose.foundation.layout.fillMaxSize
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
 * `TimelineScreen` の screenshot baseline。HomeTimelineScreen は ViewModel をラップした
 * 薄い Composable なので、その中身の presentational な `TimelineScreen` を直接描画する。
 *
 * カバレッジ:
 * - normal: 3 件の投稿が並ぶ通常状態
 * - empty: ロード完了かつ items が空（EmptyState）
 * - error: 初回ロード失敗（ErrorFallback）
 *
 * baseline PNG の実生成は CI / 開発者マシンで
 * `./gradlew :feature:timeline:ui:recordRoborazziDebug` を実行する運用。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [SCREENSHOT_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeTimelineScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val roborazziRule =
        RoborazziRule(
            composeRule = composeTestRule,
            captureRoot = composeTestRule.onRoot(),
        )

    @Test
    fun homeTimeline_normal() {
        composeTestRule.setContent {
            ScreenshotScaffold {
                TimelineScreen(
                    state = TestFactories.populatedTimeline(),
                    canLike = true,
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenPost = {},
                    onOpenAuthor = {},
                    onReply = {},
                    onToggleLike = {},
                )
            }
        }
        composeTestRule
            .onRoot()
            .captureRoboImage(filePath = "$BASELINE_DIR/HomeTimeline_normal.png")
    }

    @Test
    fun homeTimeline_empty() {
        composeTestRule.setContent {
            ScreenshotScaffold {
                TimelineScreen(
                    state = TestFactories.emptyTimeline(),
                    canLike = true,
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenPost = {},
                    onOpenAuthor = {},
                    onReply = {},
                    onToggleLike = {},
                )
            }
        }
        composeTestRule
            .onRoot()
            .captureRoboImage(filePath = "$BASELINE_DIR/HomeTimeline_empty.png")
    }

    @Test
    fun homeTimeline_error() {
        composeTestRule.setContent {
            ScreenshotScaffold {
                TimelineScreen(
                    state = TestFactories.errorTimeline(),
                    canLike = true,
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenPost = {},
                    onOpenAuthor = {},
                    onReply = {},
                    onToggleLike = {},
                )
            }
        }
        composeTestRule
            .onRoot()
            .captureRoboImage(filePath = "$BASELINE_DIR/HomeTimeline_error.png")
    }
}

@Composable
private fun ScreenshotScaffold(content: @Composable () -> Unit) {
    FujuTheme(darkTheme = false) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
    }
}

/** Robolectric で使う Android SDK level。 */
private const val SCREENSHOT_SDK: Int = 34

/** baseline PNG の出力先ディレクトリ（module からの相対パス）。 */
private const val BASELINE_DIR: String = "src/androidUnitTest/roborazzi"
