package dev.fuju.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import dev.fuju.composeApp.ComposeAppRoot
import dev.fuju.composeApp.di.AppContainer
import dev.fuju.core.error.AuthException
import dev.fuju.feature.auth.domain.OAuthCallbackParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer

    // 現在進行中の OAuth callback coroutine。新しい callback が来たら cancel する。
    private var callbackJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        container =
            AppContainer(
                authCoreBaseUrl = BuildConfig.AUTH_CORE_BASE_URL,
                fujuApiBaseUrl = BuildConfig.FUJU_API_BASE_URL,
                verboseLogging = BuildConfig.DEBUG,
            )

        intent?.handleOAuthCallback()

        setContent {
            ComposeAppRoot(deps = container.asAppDependencies())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.handleOAuthCallback()
    }

    override fun onDestroy() {
        callbackJob?.cancel()
        if (::container.isInitialized) {
            container.close()
        }
        super.onDestroy()
    }

    private fun Intent.handleOAuthCallback() {
        val uri = data ?: return
        val callback = OAuthCallbackParser.parse(uri.toString()) ?: return
        callbackJob?.cancel()
        callbackJob =
            lifecycleScope.launch {
                try {
                    container.authStateMachine.completeSocialCallback(
                        provider = callback.provider,
                        state = callback.state,
                        code = callback.code,
                    )
                } catch (e: AuthException) {
                    // AuthStateMachine が status = Unauthenticated + error に遷移するので
                    // UI 側で表示される。ここは debug log のみ（state / code は含めない）。
                    Log.w(TAG, "OAuth callback failed: ${e.code}")
                }
            }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
