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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer

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
        if (::container.isInitialized) {
            container.close()
        }
        super.onDestroy()
    }

    private fun Intent.handleOAuthCallback() {
        val uri = data ?: return
        val callback = OAuthCallbackParser.parse(uri.toString()) ?: return
        lifecycleScope.launch {
            try {
                container.authStateMachine.completeSocialCallback(
                    provider = callback.provider,
                    state = callback.state,
                    code = callback.code,
                )
            } catch (e: AuthException) {
                Log.w(TAG, "OAuth callback failed: ${e.code}", e)
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
