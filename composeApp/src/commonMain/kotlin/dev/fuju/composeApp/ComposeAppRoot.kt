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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.fuju.composeApp.nav.FujuDestination
import dev.fuju.composeApp.shell.FujuShell
import dev.fuju.core.domain.AuthStatus
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.core.ui.theme.FujuTheme
import dev.fuju.feature.auth.domain.AuthStateMachine
import dev.fuju.feature.auth.ui.AuthErrorMessages
import dev.fuju.feature.auth.ui.LoginForm
import dev.fuju.feature.auth.ui.MFAChallenge

/**
 * KMP アプリのルート Composable。Android / iOS のどちらからも呼ばれる。
 *
 * `androidx.navigation:navigation-compose` (KMP 2.9.0) の NavHost を使い、
 * [FujuDestination] の sealed route 型で型安全に遷移する。認証状態は
 * [AuthStateMachine] から購読し、未認証と認証済みで start destination を切替える。
 *
 * 認証済みは [FujuShell] が `Scaffold` + `NavigationBar` + `TopAppBar` の形で包み、
 * 未認証時は shell を挟まず LoginForm を単独で表示する（bottom bar 不要のため）。
 */
@Composable
fun ComposeAppRoot(
    deps: AppDependencies,
    modifier: Modifier = Modifier,
) {
    val snapshot by deps.authStateMachine.state.collectAsState()

    LaunchedEffect(Unit) {
        deps.authStateMachine.bootstrap()
    }

    FujuTheme {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (snapshot.status) {
                AuthStatus.Idle, AuthStatus.Authenticating -> LoadingScreen()
                AuthStatus.Error ->
                    ErrorScreen(
                        message = AuthErrorMessages.toMessage(snapshot.error) ?: "エラーが発生しました",
                    )
                AuthStatus.MFARequired ->
                    MFAChallenge(
                        onVerify = { code, rc -> deps.authStateMachine.verifyMFA(code, rc) },
                        onCancel = { deps.authStateMachine.cancelMFA() },
                        attempts = snapshot.mfaAttempts,
                    )
                AuthStatus.Unauthenticated -> UnauthenticatedRoot(deps = deps)
                AuthStatus.Authenticated -> AuthenticatedRoot(deps = deps)
            }
        }
    }
}

/**
 * 未認証時のルート。Login 画面のみ。shell（bottom bar）は挟まない。
 */
@Composable
private fun UnauthenticatedRoot(deps: AppDependencies) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = FujuDestination.Login) {
        composable<FujuDestination.Login> {
            LoginForm(
                onLogin = { id, pw -> deps.authStateMachine.login(id, pw) },
                onLoginWithSocial = { /* フェーズ 13 で OAuth callback を経由 */ },
            )
        }
    }
}

/**
 * 認証済みルート。`FujuShell` が NavHost を内包し、4 タブと子画面を配置する。
 * shell 側は start destination を `HomeTimeline` 固定とする（初期は常にホームタブ）。
 *
 * `rememberNavController()` をここで呼んでいるので、認証状態が Unauthenticated に
 * 遷移した時にこの Composable が退場し、NavHost の内部状態もそのまま GC される。
 * 結果として「ログアウト → 再ログイン時にホームタブから始まる」挙動になる。
 */
@Composable
private fun AuthenticatedRoot(deps: AppDependencies) {
    val navController = rememberNavController()
    FujuShell(deps = deps, navController = navController)
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
    val timelineRepository: dev.fuju.feature.timeline.domain.TimelineRepository,
)
