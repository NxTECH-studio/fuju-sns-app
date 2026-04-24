package dev.fuju.composeApp.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import dev.fuju.core.domain.AuthStatus
import dev.fuju.core.domain.Post
import dev.fuju.core.ui.components.EmptyState
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.feature.timeline.domain.PostDetailViewModel
import dev.fuju.feature.timeline.domain.TimelineKind
import dev.fuju.feature.timeline.domain.TimelineViewModel
import dev.fuju.feature.timeline.ui.ComposerDialog
import dev.fuju.feature.timeline.ui.GlobalTimelineScreen
import dev.fuju.feature.timeline.ui.HomeTimelineScreen
import dev.fuju.feature.timeline.ui.PostDetailScreen
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
    val authSnapshot by deps.authStateMachine.state.collectAsState()
    val canLike = authSnapshot.status == AuthStatus.Authenticated

    // Composer は shell 全体から起動するため shell 自身の state に持つ。
    // `newPost` = 新規投稿、`replyTo` = 返信先 post。両立しない。
    var composerMode by remember { mutableStateOf<ComposerMode>(ComposerMode.Closed) }

    // Home/Global timeline は Shell のライフタイム中 1 つずつ保持し、タブ切り替えで
    // 同じ state を使い続ける（React 版の Router + hooks が暗黙にやっていたキャッシュ）。
    val homeViewModel =
        remember(deps.timelineRepository, coroutineScope) {
            TimelineViewModel(deps.timelineRepository, TimelineKind.Home, coroutineScope)
        }
    val globalViewModel =
        remember(deps.timelineRepository, coroutineScope) {
            TimelineViewModel(deps.timelineRepository, TimelineKind.Global, coroutineScope)
        }

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
        floatingActionButton = {
            if (canLike && currentDestination?.isComposerFabVisible() == true) {
                ExtendedFloatingActionButton(
                    onClick = { composerMode = ComposerMode.NewPost },
                    text = { Text("投稿") },
                    icon = { Text("＋", style = MaterialTheme.typography.titleLarge) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = FujuDestination.HomeTimeline,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable<FujuDestination.HomeTimeline> {
                HomeTimelineScreen(
                    viewModel = homeViewModel,
                    canLike = canLike,
                    onOpenPost = { post -> navController.navigate(FujuDestination.PostDetail(post.id)) },
                    onOpenAuthor = { post ->
                        post.author?.let { navController.navigate(FujuDestination.Profile(it.sub)) }
                    },
                    onReply = { post -> composerMode = ComposerMode.Reply(post) },
                )
            }
            composable<FujuDestination.GlobalTimeline> {
                GlobalTimelineScreen(
                    viewModel = globalViewModel,
                    canLike = canLike,
                    onOpenPost = { post -> navController.navigate(FujuDestination.PostDetail(post.id)) },
                    onOpenAuthor = { post ->
                        post.author?.let { navController.navigate(FujuDestination.Profile(it.sub)) }
                    },
                    onReply = { post -> composerMode = ComposerMode.Reply(post) },
                )
            }
            composable<FujuDestination.MyProfile> {
                PlaceholderScreen(label = "My Profile")
            }
            composable<FujuDestination.AdminBadges> {
                PlaceholderScreen(label = "Admin Badges")
            }
            composable<FujuDestination.PostDetail> { backStack ->
                val args = backStack.toRoute<FujuDestination.PostDetail>()
                val detailScope = rememberCoroutineScope()
                val detailViewModel =
                    remember(args.postId, deps.timelineRepository, detailScope) {
                        PostDetailViewModel(
                            repository = deps.timelineRepository,
                            postId = args.postId,
                            scope = detailScope,
                        )
                    }
                PostDetailScreen(
                    viewModel = detailViewModel,
                    canLike = canLike,
                    onOpenAuthor = { post ->
                        post.author?.let { navController.navigate(FujuDestination.Profile(it.sub)) }
                    },
                    onOpenReply = { reply -> navController.navigate(FujuDestination.PostDetail(reply.id)) },
                    onRequestReplyComposer = { target -> composerMode = ComposerMode.Reply(target) },
                )
            }
            composable<FujuDestination.Profile> { backStack ->
                val args = backStack.toRoute<FujuDestination.Profile>()
                PlaceholderScreen(label = "Profile @${args.publicId}")
            }
        }
    }

    when (val mode = composerMode) {
        ComposerMode.Closed -> Unit
        ComposerMode.NewPost ->
            ComposerDialog(
                onDismiss = { composerMode = ComposerMode.Closed },
                onSubmit = { content ->
                    homeViewModel.createPost(content = content)
                },
            )
        is ComposerMode.Reply ->
            ComposerDialog(
                parentHint = mode.target.author?.displayName ?: "@${mode.target.userId}",
                onDismiss = { composerMode = ComposerMode.Closed },
                onSubmit = { content ->
                    // 返信はタイムラインの先頭には出ず、詳細画面側で append される想定。
                    // ここでは Repository 直叩きで投稿するだけ。
                    deps.timelineRepository.createPost(
                        content = content,
                        imageIds = emptyList(),
                        parentPostId = mode.target.id,
                    )
                },
            )
    }
}

private sealed interface ComposerMode {
    data object Closed : ComposerMode

    data object NewPost : ComposerMode

    data class Reply(
        val target: Post,
    ) : ComposerMode
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
            description = "フェーズ 3 以降で profile / admin を接続します。",
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
 * 新規投稿 FAB を表示するのはタイムラインの 2 タブだけ。詳細画面は画面内の
 * 返信ボタンから composer を起動する想定のため FAB は不要。
 */
private fun NavDestination.isComposerFabVisible(): Boolean =
    hasRoute(FujuDestination.HomeTimeline::class) || hasRoute(FujuDestination.GlobalTimeline::class)

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
