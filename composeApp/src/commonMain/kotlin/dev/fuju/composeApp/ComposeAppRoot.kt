package dev.fuju.composeApp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fuju.composeApp.nav.FujuDestination
import dev.fuju.core.domain.AuthStatus
import dev.fuju.core.ui.components.EmptyState
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.core.ui.theme.FujuTheme
import dev.fuju.feature.auth.domain.AuthStateMachine
import dev.fuju.feature.auth.ui.AuthErrorMessages
import dev.fuju.feature.auth.ui.LoginForm
import dev.fuju.feature.auth.ui.MFAChallenge

/**
 * KMP アプリのルート Composable。Android / iOS のどちらからも呼ばれる。
 *
 * 現段階では最低限のナビゲーション（login → timeline → profile）だけを実装。
 * Navigation ライブラリは後続タスクで確定するため、`FujuDestination` + 素の state
 * でルーティングする。
 */
@Composable
fun ComposeAppRoot(
    deps: AppDependencies,
    modifier: Modifier = Modifier,
) {
    val snapshot by deps.authStateMachine.state.collectAsState()
    var destination by remember { mutableStateOf<FujuDestination>(FujuDestination.Login) }

    LaunchedEffect(Unit) {
        deps.authStateMachine.bootstrap()
    }

    LaunchedEffect(snapshot.status) {
        if (snapshot.status == AuthStatus.Authenticated && destination == FujuDestination.Login) {
            destination = FujuDestination.HomeTimeline
        }
        if (snapshot.status == AuthStatus.Unauthenticated && destination != FujuDestination.Login) {
            destination = FujuDestination.Login
        }
    }

    FujuTheme {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (snapshot.status) {
                AuthStatus.Idle, AuthStatus.Authenticating -> LoadingScreen()
                AuthStatus.Error -> ErrorScreen(message = AuthErrorMessages.toMessage(snapshot.error) ?: "エラーが発生しました")
                AuthStatus.MFARequired -> MFAChallenge(
                    onVerify = { code, rc -> deps.authStateMachine.verifyMFA(code, rc) },
                    onCancel = { deps.authStateMachine.cancelMFA() },
                    attempts = snapshot.mfaAttempts,
                )
                AuthStatus.Unauthenticated -> LoginForm(
                    onLogin = { id, pw -> deps.authStateMachine.login(id, pw) },
                    onLoginWithSocial = { /* フェーズ 2 end */ },
                )
                AuthStatus.Authenticated -> AuthenticatedScreen(deps, destination) { destination = it }
            }
        }
    }
}

@Composable
private fun AuthenticatedScreen(
    deps: AppDependencies,
    destination: FujuDestination,
    onNavigate: (FujuDestination) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(FujuDimens.SpaceL)) {
        Text(
            text = "Fuju (${destination.label})",
            style = MaterialTheme.typography.headlineMedium,
        )
        EmptyState(
            title = "準備中",
            description = "フェーズ 3 以降で timeline / profile / admin を接続します。",
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("エラー", style = MaterialTheme.typography.headlineMedium)
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * コンポジションに注入する依存。Android / iOS の側で DI コンテナから組み立てる。
 */
class AppDependencies(
    val authStateMachine: AuthStateMachine,
)
