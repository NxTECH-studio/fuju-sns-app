package dev.fuju.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.fuju.composeApp.ComposeAppRoot
import dev.fuju.composeApp.di.AppContainer

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        container = AppContainer(
            authCoreBaseUrl = BuildConfig.AUTH_CORE_BASE_URL,
            fujuApiBaseUrl = BuildConfig.FUJU_API_BASE_URL,
            verboseLogging = BuildConfig.DEBUG,
        )

        // Deep link (OAuth callback) があればバスに流す。本番では state / code を取り出して
        // AuthStateMachine.completeSocialCallback に渡す。
        intent?.handleOAuthCallback()

        setContent {
            ComposeAppRoot(deps = container.asAppDependencies())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.handleOAuthCallback()
    }

    private fun Intent.handleOAuthCallback() {
        val uri = data ?: return
        if (uri.scheme != "fuju" || uri.host != "auth" || uri.path != "/callback") return
        // TODO(フェーズ 4): provider / state / code を抽出して AuthStateMachine に通知
    }
}
