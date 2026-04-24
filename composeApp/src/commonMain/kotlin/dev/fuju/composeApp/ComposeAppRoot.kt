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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
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
 * `androidx.navigation:navigation-compose` (KMP 2.9.0) の NavHost を使い、
 * [FujuDestination] の sealed route 型で型安全に遷移する。認証状態は
 * [AuthStateMachine] から購読し、未認証と認証済みで start destination を切替える。
 */
@Composable
fun ComposeAppRoot(
    deps: AppDependencies,
    modifier: Modifier = Modifier,
) {
    val snapshot by deps.authStateMachine.state.collectAsState()
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        deps.authStateMachine.bootstrap()
    }

    LaunchedEffect(snapshot.status) {
        when (snapshot.status) {
            AuthStatus.Authenticated ->
                navController.navigate(FujuDestination.HomeTimeline) {
                    popUpTo(FujuDestination.Login) { inclusive = true }
                    launchSingleTop = true
                }
            AuthStatus.Unauthenticated ->
                navController.navigate(FujuDestination.Login) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            else -> Unit
        }
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
                AuthStatus.Unauthenticated, AuthStatus.Authenticated ->
                    FujuNavHost(deps = deps, navController = navController)
            }
        }
    }
}

@Composable
private fun FujuNavHost(
    deps: AppDependencies,
    navController: NavHostController,
) {
    val snapshot by deps.authStateMachine.state.collectAsState()
    val startDestination: FujuDestination =
        if (snapshot.status == AuthStatus.Authenticated) {
            FujuDestination.HomeTimeline
        } else {
            FujuDestination.Login
        }

    NavHost(navController = navController, startDestination = startDestination) {
        composable<FujuDestination.Login> {
            LoginForm(
                onLogin = { id, pw -> deps.authStateMachine.login(id, pw) },
                onLoginWithSocial = { /* フェーズ 13 で OAuth callback を経由 */ },
            )
        }
        composable<FujuDestination.HomeTimeline> {
            PlaceholderScreen(label = "Home Timeline")
        }
        composable<FujuDestination.GlobalTimeline> {
            PlaceholderScreen(label = "Global Timeline")
        }
        composable<FujuDestination.PostDetail> { backStack ->
            val args = backStack.toRoute<FujuDestination.PostDetail>()
            PlaceholderScreen(label = "Post ${args.postId}")
        }
        composable<FujuDestination.Profile> { backStack ->
            val args = backStack.toRoute<FujuDestination.Profile>()
            PlaceholderScreen(label = "Profile @${args.publicId}")
        }
        composable<FujuDestination.AdminBadges> {
            PlaceholderScreen(label = "Admin Badges")
        }
    }
}

@Composable
private fun PlaceholderScreen(label: String) {
    Column(modifier = Modifier.fillMaxSize().padding(FujuDimens.SpaceL)) {
        Text(text = "Fuju ($label)", style = MaterialTheme.typography.headlineMedium)
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
