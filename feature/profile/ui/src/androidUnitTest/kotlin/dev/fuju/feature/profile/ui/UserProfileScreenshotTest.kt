package dev.fuju.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.core.ui.theme.FujuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `ProfileHeader` + `FollowButton` の screenshot baseline。
 *
 * ViewModel を通す `UserProfileScreen` ではなく、状態を直接 props で渡せる presentational な
 * 下位 Composable を描画する。これにより backend / ViewModel の都合と切り離して UI 差分のみを
 * 検出できる。
 *
 * カバレッジ:
 * - header: バナー + アイコン + bio + badges + フォロー数
 * - followButton_notFollowing / _following / _pending: 3 状態
 *
 * baseline PNG の実生成は CI / 開発者マシンで
 * `./gradlew :feature:profile:ui:recordRoborazziDebug` を実行する運用。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [SCREENSHOT_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UserProfileScreenshotTest {
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
    fun profileHeader_default() {
        composeTestRule.setContent {
            ScreenshotScaffold {
                ProfileHeader(
                    user = TestFactories.fakeProfileUser(),
                    followersCount = 128,
                    onOpenFollowers = {},
                    onOpenFollowing = {},
                    actions = {
                        FollowButton(
                            following = false,
                            onToggle = {},
                        )
                    },
                )
            }
        }
        composeTestRule
            .onRoot()
            .captureRoboImage(filePath = "src/androidUnitTest/roborazzi/ProfileHeader_default.png")
    }

    @Test
    fun followButton_notFollowing() {
        composeTestRule.setContent {
            ScreenshotScaffold {
                PaddedColumn {
                    FollowButton(
                        following = false,
                        onToggle = {},
                    )
                }
            }
        }
        composeTestRule
            .onRoot()
            .captureRoboImage(filePath = "src/androidUnitTest/roborazzi/FollowButton_notFollowing.png")
    }

    @Test
    fun followButton_following() {
        composeTestRule.setContent {
            ScreenshotScaffold {
                PaddedColumn {
                    FollowButton(
                        following = true,
                        onToggle = {},
                    )
                }
            }
        }
        composeTestRule
            .onRoot()
            .captureRoboImage(filePath = "src/androidUnitTest/roborazzi/FollowButton_following.png")
    }

    @Test
    fun followButton_pending() {
        composeTestRule.setContent {
            ScreenshotScaffold {
                PaddedColumn {
                    FollowButton(
                        following = false,
                        onToggle = {},
                        pending = true,
                    )
                }
            }
        }
        composeTestRule
            .onRoot()
            .captureRoboImage(filePath = "src/androidUnitTest/roborazzi/FollowButton_pending.png")
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

@Composable
private fun PaddedColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

/** Robolectric で使う Android SDK level。 */
private const val SCREENSHOT_SDK: Int = 34
