package dev.fuju.composeApp.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import dev.fuju.composeApp.AppDependencies
import dev.fuju.composeApp.nav.FujuDestination
import dev.fuju.core.ui.components.EmptyState
import dev.fuju.core.ui.theme.FujuDimens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * 認証済みユーザー向けのアプリシェル。Material3 `Scaffold` に `NavigationBar`（4 タブ）と
 * `TopAppBar`（タイトル + logout メニュー）を載せ、中央の NavHost に各タブ / 子画面を配置する。
 *
 * React 版 `src/routes/RootLayoutRoute.tsx` の構造を踏襲（top bar + nav + main）。
 * 今回は **フラット NavHost** で子画面も同じ階層に並べる。nested graph は次タスクで検討。
 *
 * アイコンは Compose Multiplatform に material-icons が標準で入らないため、
 * React 版と同じくラベルテキストだけで構成する。将来アイコンが欲しくなったら
 * `material-icons-extended` などを追加する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FujuShell(
    deps: AppDependencies,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination: NavDestination? = backStackEntry?.destination
    val currentTitle = currentDestination.titleForDestination()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(currentTitle) },
                actions = {
                    ShellOverflowMenu(
                        onLogout = { coroutineScope.launchLogout(deps) },
                    )
                },
            )
        },
        bottomBar = {
            FujuBottomBar(
                currentDestination = currentDestination,
                onSelectTab = { tab -> navController.navigateToTab(tab) },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = FujuDestination.HomeTimeline,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable<FujuDestination.HomeTimeline> {
                PlaceholderScreen(label = "Home Timeline")
            }
            composable<FujuDestination.GlobalTimeline> {
                PlaceholderScreen(label = "Global Timeline")
            }
            composable<FujuDestination.MyProfile> {
                PlaceholderScreen(label = "My Profile")
            }
            composable<FujuDestination.AdminBadges> {
                PlaceholderScreen(label = "Admin Badges")
            }
            composable<FujuDestination.PostDetail> { backStack ->
                val args = backStack.toRoute<FujuDestination.PostDetail>()
                PlaceholderScreen(label = "Post ${args.postId}")
            }
            composable<FujuDestination.Profile> { backStack ->
                val args = backStack.toRoute<FujuDestination.Profile>()
                PlaceholderScreen(label = "Profile @${args.publicId}")
            }
        }
    }
}

/** shell の 4 タブ定義。表示順 = NavigationBar の左→右。 */
private val ShellTabs: List<ShellTab> =
    listOf(
        ShellTab(FujuDestination.HomeTimeline, FujuDestination.HomeTimeline::class, "Home"),
        ShellTab(FujuDestination.GlobalTimeline, FujuDestination.GlobalTimeline::class, "Global"),
        ShellTab(FujuDestination.MyProfile, FujuDestination.MyProfile::class, "Profile"),
        ShellTab(FujuDestination.AdminBadges, FujuDestination.AdminBadges::class, "Admin"),
    )

private data class ShellTab(
    val route: FujuDestination,
    val routeClass: KClass<out FujuDestination>,
    val label: String,
)

@Composable
private fun FujuBottomBar(
    currentDestination: NavDestination?,
    onSelectTab: (FujuDestination) -> Unit,
) {
    NavigationBar {
        ShellTabs.forEach { tab ->
            val selected =
                currentDestination?.hierarchy?.any { dest -> dest.hasRoute(tab.routeClass) } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab.route) },
                icon = { Text(tab.label.first().toString(), style = MaterialTheme.typography.titleMedium) },
                label = { Text(tab.label) },
            )
        }
    }
}

/**
 * `NavDestination.hierarchy` は androidx 系には既に定義があるが、JetBrains fork では
 * シグネチャが同名で提供されない場合があるため、ここで小さな拡張として書き出す。
 * 自分自身から親へ辿る sequence を返す。
 */
private val NavDestination.hierarchy: Sequence<NavDestination>
    get() = generateSequence(this) { it.parent }

@Composable
private fun ShellOverflowMenu(onLogout: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("メニュー")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("ログアウト") },
                onClick = {
                    expanded = false
                    onLogout()
                },
            )
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

private fun NavDestination?.titleForDestination(): String {
    if (this == null) return "Fuju"
    return ShellTabs.firstOrNull { tab -> this.hasRoute(tab.routeClass) }?.label
        ?: when {
            hasRoute(FujuDestination.PostDetail::class) -> "投稿"
            hasRoute(FujuDestination.Profile::class) -> "プロフィール"
            else -> "Fuju"
        }
}

/**
 * タブ間遷移時に back stack を state ごと保存/復元する。
 * start destination (= [FujuDestination.HomeTimeline]) まで pop し、
 * 同じタブを再タップした時に重複エントリを作らないよう launchSingleTop を立てる。
 *
 * JetBrains の KMP fork では `NavGraph.findStartDestination().id` が commonMain に
 * 露出しないため、型安全 route を引数に取る `popUpTo<T>` オーバーロードを使う。
 */
private fun NavHostController.navigateToTab(tab: FujuDestination) {
    navigate(tab) {
        popUpTo<FujuDestination.HomeTimeline> {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun CoroutineScope.launchLogout(deps: AppDependencies) {
    launch {
        deps.authStateMachine.logout()
    }
}
