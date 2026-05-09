package dev.fuju.composeApp.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
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
import dev.fuju.feature.admin.domain.AdminBadgesViewModel
import dev.fuju.feature.admin.domain.AdminUserBadgesViewModel
import dev.fuju.feature.admin.ui.AdminBadgesScreen
import dev.fuju.feature.admin.ui.AdminUserBadgesScreen
import dev.fuju.feature.profile.domain.FollowListKind
import dev.fuju.feature.profile.domain.FollowListViewModel
import dev.fuju.feature.profile.domain.MeViewModel
import dev.fuju.feature.profile.domain.ProfileViewModel
import dev.fuju.feature.profile.ui.FollowListScreen
import dev.fuju.feature.profile.ui.SettingsProfileSection
import dev.fuju.feature.profile.ui.UserProfileScreen
import dev.fuju.feature.timeline.domain.PostDetailViewModel
import dev.fuju.feature.timeline.domain.TimelineKind
import dev.fuju.feature.timeline.domain.TimelineViewModel
import dev.fuju.feature.timeline.ui.ComposerDialog
import dev.fuju.feature.timeline.ui.GlobalTimelineScreen
import dev.fuju.feature.timeline.ui.PostDetailScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * 認証済みユーザー向けのアプリシェル。Material3 `Scaffold` に `NavigationBar` と
 * `TopAppBar`（タイトル + logout メニュー）を載せ、中央の NavHost に各タブ / 子画面を配置する。
 *
 * frontend `src/routes/RootLayoutRoute.tsx` の構造を踏襲する:
 * - start destination は **GlobalTimeline**（`/`）。Home timeline は撤去済み。
 * - tabs: Global / Profile / Settings / Admin（isAdmin のみ）
 * - Composer FAB は GlobalTimeline / PostDetail でのみ表示
 *
 * `isAdmin` は `MeViewModel` 経由で `/me` から取得する。tabs はそれを `remember` で
 * 観測して動的に組み立てる。
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
    val coroutineScope = rememberCoroutineScope()
    val authSnapshot by deps.authStateMachine.state.collectAsState()
    val canLike = authSnapshot.status == AuthStatus.Authenticated

    // shell 全体で共有する Me。tab 表示の isAdmin 判定 / Settings の me セクション両方で参照する。
    val meViewModel =
        remember(deps.profileRepository, coroutineScope) {
            MeViewModel(deps.profileRepository, coroutineScope)
        }
    val meStatus by meViewModel.status.collectAsState()
    val isAdmin = (meStatus as? MeViewModel.Status.Ready)?.me?.isAdmin == true

    val tabs = remember(isAdmin) { buildShellTabs(isAdmin = isAdmin) }
    val currentTitle = currentDestination.titleForDestination(tabs)

    // Composer は shell 全体から起動するため shell 自身の state に持つ。
    var composerMode by remember { mutableStateOf<ComposerMode>(ComposerMode.Closed) }

    // Global timeline は Shell のライフタイム中 1 つだけ保持し、タブ復帰時に同じ state を使い続ける。
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
                    ShellOverflowMenu(onLogout = { coroutineScope.launchLogout(deps) })
                },
            )
        },
        bottomBar = {
            FujuBottomBar(
                tabs = tabs,
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
            startDestination = FujuDestination.GlobalTimeline,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable<FujuDestination.GlobalTimeline> {
                GlobalTimelineScreen(
                    viewModel = globalViewModel,
                    canLike = canLike,
                    onOpenPost = { post -> navController.navigate(FujuDestination.PostDetail(post.id)) },
                    onOpenAuthor = { post ->
                        post.author?.let { navController.navigate(FujuDestination.Profile(it.sub)) }
                    },
                    onReply = { post -> composerMode = ComposerMode.Reply(post) },
                    telemetryDispatcher = deps.telemetryDispatcher,
                )
            }
            composable<FujuDestination.MyProfile> {
                val profileScope = rememberCoroutineScope()
                val profileViewModel =
                    remember(deps.profileRepository, profileScope) {
                        ProfileViewModel(
                            repository = deps.profileRepository,
                            targetSub = null,
                            scope = profileScope,
                        )
                    }
                val profileState by profileViewModel.state.collectAsState()
                val mySub = profileState.user?.sub
                if (mySub == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val timelineViewModel =
                        remember(mySub, deps.timelineRepository, profileScope) {
                            TimelineViewModel(
                                repository = deps.timelineRepository,
                                kind = TimelineKind.User(mySub),
                                scope = profileScope,
                            )
                        }
                    UserProfileScreen(
                        profileViewModel = profileViewModel,
                        timelineViewModel = timelineViewModel,
                        canLike = canLike,
                        onOpenPost = { post ->
                            navController.navigate(FujuDestination.PostDetail(post.id))
                        },
                        onOpenAuthor = { post ->
                            post.author?.let { navController.navigate(FujuDestination.Profile(it.sub)) }
                        },
                        onReply = { post -> composerMode = ComposerMode.Reply(post) },
                        onOpenFollowers = { sub ->
                            navController.navigate(FujuDestination.FollowList(sub, followers = true))
                        },
                        onOpenFollowing = { sub ->
                            navController.navigate(FujuDestination.FollowList(sub, followers = false))
                        },
                        onOpenEdit = { navController.navigate(FujuDestination.SettingsProfile) },
                    )
                }
            }
            composable<FujuDestination.SettingsRoot> {
                SettingsHubScreen(
                    deps = deps,
                    navController = navController,
                )
            }
            composable<FujuDestination.SettingsProfile> {
                SettingsHubScreen(
                    deps = deps,
                    navController = navController,
                )
            }
            composable<FujuDestination.AdminBadges> {
                AdminBadgesRoute(
                    deps = deps,
                    isAdmin = isAdmin,
                    isMeReady = meStatus is MeViewModel.Status.Ready,
                    navController = navController,
                )
            }
            composable<FujuDestination.AdminUserBadges> {
                AdminUserBadgesRoute(
                    deps = deps,
                    isAdmin = isAdmin,
                    isMeReady = meStatus is MeViewModel.Status.Ready,
                    navController = navController,
                )
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
                val profileScope = rememberCoroutineScope()
                val profileViewModel =
                    remember(args.publicId, deps.profileRepository, profileScope) {
                        ProfileViewModel(
                            repository = deps.profileRepository,
                            targetSub = args.publicId,
                            scope = profileScope,
                        )
                    }
                val timelineViewModel =
                    remember(args.publicId, deps.timelineRepository, profileScope) {
                        TimelineViewModel(
                            repository = deps.timelineRepository,
                            kind = TimelineKind.User(args.publicId),
                            scope = profileScope,
                        )
                    }
                UserProfileScreen(
                    profileViewModel = profileViewModel,
                    timelineViewModel = timelineViewModel,
                    canLike = canLike,
                    onOpenPost = { post ->
                        navController.navigate(FujuDestination.PostDetail(post.id))
                    },
                    onOpenAuthor = { post ->
                        post.author?.let { navController.navigate(FujuDestination.Profile(it.sub)) }
                    },
                    onReply = { post -> composerMode = ComposerMode.Reply(post) },
                    onOpenFollowers = { sub ->
                        navController.navigate(FujuDestination.FollowList(sub, followers = true))
                    },
                    onOpenFollowing = { sub ->
                        navController.navigate(FujuDestination.FollowList(sub, followers = false))
                    },
                    onOpenEdit = { navController.navigate(FujuDestination.SettingsProfile) },
                )
            }
            composable<FujuDestination.FollowList> { backStack ->
                val args = backStack.toRoute<FujuDestination.FollowList>()
                val kind = if (args.followers) FollowListKind.Followers else FollowListKind.Following
                val listScope = rememberCoroutineScope()
                val listViewModel =
                    remember(args.sub, kind, deps.profileRepository, listScope) {
                        FollowListViewModel(
                            repository = deps.profileRepository,
                            targetSub = args.sub,
                            kind = kind,
                            scope = listScope,
                        )
                    }
                FollowListScreen(
                    viewModel = listViewModel,
                    onOpenUser = { user ->
                        navController.navigate(FujuDestination.Profile(user.sub))
                    },
                )
            }
        }
    }

    when (val mode = composerMode) {
        ComposerMode.Closed -> Unit
        ComposerMode.NewPost ->
            ComposerDialog(
                onDismiss = { composerMode = ComposerMode.Closed },
                onSubmit = { content ->
                    globalViewModel.createPost(content = content)
                },
            )
        is ComposerMode.Reply ->
            ComposerDialog(
                parentHint = mode.target.author?.displayName ?: "@${mode.target.userId}",
                onDismiss = { composerMode = ComposerMode.Closed },
                onSubmit = { content ->
                    deps.timelineRepository.createPost(
                        content = content,
                        parentPostId = mode.target.id,
                    )
                },
            )
    }
}

/**
 * 設定ハブ画面（`/settings` または `/settings/profile`）。
 * 現状の項目はプロフィールのみなので、左ペインから選んでも常に同じセクションを表示する。
 */
@Composable
private fun SettingsHubScreen(
    deps: AppDependencies,
    navController: NavHostController,
) {
    val items = remember { listOf(SettingsItem(id = "profile", label = "プロフィール")) }
    val activeId = "profile"
    val scope = rememberCoroutineScope()
    val profileViewModel =
        remember(deps.profileRepository, scope) {
            ProfileViewModel(
                repository = deps.profileRepository,
                targetSub = null,
                scope = scope,
            )
        }
    SettingsShell(
        items = items,
        activeItemId = activeId,
        onSelect = { /* 単一項目なので no-op */ },
    ) {
        SettingsProfileSection(
            viewModel = profileViewModel,
            onSave = { sub ->
                navController.navigate(FujuDestination.Profile(sub)) {
                    popUpTo(FujuDestination.GlobalTimeline) {
                        saveState = true
                    }
                }
            },
            onCancel = { navController.popBackStack() },
        )
    }
}

@Composable
private fun AdminBadgesRoute(
    deps: AppDependencies,
    isAdmin: Boolean,
    isMeReady: Boolean,
    navController: NavHostController,
) {
    if (!isMeReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (!isAdmin) {
        // 認可されていないアクセスは GlobalTimeline へ replace。frontend と同じ挙動。
        androidx.compose.runtime.LaunchedEffect(Unit) {
            navController.navigate(FujuDestination.GlobalTimeline) {
                popUpTo(FujuDestination.GlobalTimeline) { inclusive = true }
            }
        }
        return
    }
    val scope = rememberCoroutineScope()
    val viewModel =
        remember(deps.adminRepository, scope) {
            AdminBadgesViewModel(deps.adminRepository, scope)
        }
    AdminBadgesScreen(
        viewModel = viewModel,
        onOpenUserBadges = { navController.navigate(FujuDestination.AdminUserBadges) },
        // badge_key は user-badges 画面で手入力する想定。frontend のクエリ連携は未対応。
        onGrantToUser = {
            navController.navigate(FujuDestination.AdminUserBadges)
        },
    )
}

@Composable
private fun AdminUserBadgesRoute(
    deps: AppDependencies,
    isAdmin: Boolean,
    isMeReady: Boolean,
    navController: NavHostController,
) {
    if (!isMeReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (!isAdmin) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            navController.navigate(FujuDestination.GlobalTimeline) {
                popUpTo(FujuDestination.GlobalTimeline) { inclusive = true }
            }
        }
        return
    }
    val scope = rememberCoroutineScope()
    val viewModel =
        remember(deps.adminRepository, deps.profileRepository, scope) {
            AdminUserBadgesViewModel(
                adminRepository = deps.adminRepository,
                profileRepository = deps.profileRepository,
                scope = scope,
            )
        }
    AdminUserBadgesScreen(
        viewModel = viewModel,
        initialBadgeKey = "",
        onBack = { navController.navigate(FujuDestination.AdminBadges) },
    )
}

private sealed interface ComposerMode {
    data object Closed : ComposerMode

    data object NewPost : ComposerMode

    data class Reply(
        val target: Post,
    ) : ComposerMode
}

/**
 * shell の NavigationBar に出すタブ。`isAdmin = true` の時だけ Admin タブが追加される。
 */
private fun buildShellTabs(isAdmin: Boolean): List<ShellTab> =
    buildList {
        add(ShellTab(FujuDestination.GlobalTimeline, FujuDestination.GlobalTimeline::class, "Global"))
        add(ShellTab(FujuDestination.MyProfile, FujuDestination.MyProfile::class, "Profile"))
        add(ShellTab(FujuDestination.SettingsRoot, FujuDestination.SettingsRoot::class, "Settings"))
        if (isAdmin) {
            add(ShellTab(FujuDestination.AdminBadges, FujuDestination.AdminBadges::class, "Admin"))
        }
    }

private data class ShellTab(
    val route: FujuDestination,
    val routeClass: KClass<out FujuDestination>,
    val label: String,
)

@Composable
private fun FujuBottomBar(
    tabs: List<ShellTab>,
    currentDestination: NavDestination?,
    onSelectTab: (FujuDestination) -> Unit,
) {
    NavigationBar {
        tabs.forEach { tab ->
            val selected = isTabSelected(tab.routeClass, currentDestination)
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

/**
 * 「現在の destination がこのタブに属するか」を判定する。
 * Settings / Admin のサブ destination もそれぞれの親タブ選択中として扱う。
 */
private fun isTabSelected(
    routeClass: KClass<out FujuDestination>,
    currentDestination: NavDestination?,
): Boolean {
    if (currentDestination == null) return false
    val direct = currentDestination.hierarchy.any { it.hasRoute(routeClass) }
    if (direct) return true
    val isSettingsTab = routeClass == FujuDestination.SettingsRoot::class
    if (isSettingsTab && currentDestination.hasRoute(FujuDestination.SettingsProfile::class)) {
        return true
    }
    val isAdminTab = routeClass == FujuDestination.AdminBadges::class
    if (isAdminTab && currentDestination.hasRoute(FujuDestination.AdminUserBadges::class)) {
        return true
    }
    return false
}

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

private fun NavDestination?.titleForDestination(tabs: List<ShellTab>): String {
    if (this == null) return "Fuju"
    val tabLabel = tabs.firstOrNull { tab -> this.hasRoute(tab.routeClass) }?.label
    return tabLabel ?: when {
        hasRoute(FujuDestination.PostDetail::class) -> "投稿"
        hasRoute(FujuDestination.Profile::class) -> "プロフィール"
        hasRoute(FujuDestination.FollowList::class) -> "フォロー一覧"
        hasRoute(FujuDestination.SettingsProfile::class) -> "プロフィール編集"
        hasRoute(FujuDestination.AdminUserBadges::class) -> "Admin / ユーザーバッジ"
        else -> "Fuju"
    }
}

/**
 * 新規投稿 FAB を表示するのは Global timeline と Post 詳細だけ。詳細画面は画面内の
 * 返信ボタンと両立させる（FAB タップ = 新規投稿、画面内ボタン = 返信）。
 */
private fun NavDestination.isComposerFabVisible(): Boolean =
    hasRoute(FujuDestination.GlobalTimeline::class) ||
        hasRoute(FujuDestination.PostDetail::class)

/**
 * タブ間遷移時に back stack を state ごと保存/復元する。
 * start destination まで pop し、同じタブを再タップした時に重複エントリを作らないよう
 * launchSingleTop を立てる。
 */
private fun NavHostController.navigateToTab(tab: FujuDestination) {
    navigate(tab) {
        popUpTo<FujuDestination.GlobalTimeline> {
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
