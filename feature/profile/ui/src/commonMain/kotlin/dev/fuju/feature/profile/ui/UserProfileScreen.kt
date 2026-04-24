package dev.fuju.feature.profile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fuju.core.domain.Post
import dev.fuju.core.ui.components.ErrorFallback
import dev.fuju.core.ui.components.FujuSecondaryButton
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.feature.profile.domain.ProfileState
import dev.fuju.feature.profile.domain.ProfileViewModel
import dev.fuju.feature.timeline.domain.PagedList
import dev.fuju.feature.timeline.domain.TimelineViewModel
import dev.fuju.feature.timeline.ui.TimelineScreen

/**
 * ユーザープロフィール画面。プロフィールヘッダ + 投稿タイムラインを縦に並べる。
 * React 版 `routes/UserProfileRoute.tsx` に相当。
 *
 * - `profileViewModel` はプロフィール本体 + follow state を管理
 * - `timelineViewModel` はそのユーザーの投稿一覧（`TimelineKind.User(sub)`）を管理
 * - Header は `TimelineScreen` の `header` スロットにそのまま差し込み、スクロール時は
 *   ヘッダも一緒に流れる UX にする
 *
 * タイムラインの先頭投稿から follow state を推測する React 版の挙動を踏襲し、
 * タイムライン初回ロード完了時に `profileViewModel.syncFollowState` を呼ぶ。
 *
 * ViewModel はここでだけ touch し、下位 Composable には state と callback を plain 型で
 * 渡す（Compose Rules の "hoist all the things"）。
 */
@Composable
fun UserProfileScreen(
    profileViewModel: ProfileViewModel,
    timelineViewModel: TimelineViewModel,
    canLike: Boolean,
    onOpenPost: (Post) -> Unit,
    onOpenAuthor: (Post) -> Unit,
    onReply: (Post) -> Unit,
    onOpenFollowers: (String) -> Unit,
    onOpenFollowing: (String) -> Unit,
    onOpenEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileState by profileViewModel.state.collectAsState()
    val timelineState by timelineViewModel.state.collectAsState()

    // タイムライン初回ロード完了時に follow state を推測して ViewModel に伝える。
    // swagger が `/users/{sub}` の応答に follow 情報を含めない回避策（React 版踏襲）。
    LaunchedEffect(timelineState.loading, timelineState.error, timelineState.items.size) {
        if (!timelineState.loading && timelineState.error == null && !profileState.followStateKnown) {
            val inferred = timelineState.items.firstOrNull()?.followingAuthor ?: false
            profileViewModel.syncFollowState(following = inferred)
        }
    }

    UserProfileContent(
        profileState = profileState,
        timelineState = timelineState,
        canLike = canLike,
        onReloadProfile = profileViewModel::reload,
        onRefreshTimeline = timelineViewModel::refresh,
        onLoadMoreTimeline = timelineViewModel::loadMore,
        onToggleLike = timelineViewModel::toggleLike,
        onToggleFollow = profileViewModel::toggleFollow,
        onOpenPost = onOpenPost,
        onOpenAuthor = onOpenAuthor,
        onReply = onReply,
        onOpenFollowers = onOpenFollowers,
        onOpenFollowing = onOpenFollowing,
        onOpenEdit = onOpenEdit,
        modifier = modifier,
    )
}

@Composable
private fun UserProfileContent(
    profileState: ProfileState,
    timelineState: PagedList<Post>,
    canLike: Boolean,
    onReloadProfile: () -> Unit,
    onRefreshTimeline: () -> Unit,
    onLoadMoreTimeline: () -> Unit,
    onToggleLike: (Post) -> Unit,
    onToggleFollow: () -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenAuthor: (Post) -> Unit,
    onReply: (Post) -> Unit,
    onOpenFollowers: (String) -> Unit,
    onOpenFollowing: (String) -> Unit,
    onOpenEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val user = profileState.user
    when {
        profileState.loading && user == null ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        user == null ->
            ErrorFallback(
                title = "プロフィールを取得できませんでした",
                message = profileState.error ?: "ユーザーが見つかりませんでした。",
                onRetry = onReloadProfile,
                modifier = modifier.fillMaxSize(),
            )
        else -> {
            val isSelf = profileState.me?.sub == user.sub
            TimelineScreen(
                state = timelineState,
                canLike = canLike,
                onRefresh = {
                    onReloadProfile()
                    onRefreshTimeline()
                },
                onLoadMore = onLoadMoreTimeline,
                onOpenPost = onOpenPost,
                onOpenAuthor = onOpenAuthor,
                onReply = onReply,
                onToggleLike = onToggleLike,
                modifier = modifier,
                emptyTitle = "まだ投稿がありません",
                emptyDescription = "このユーザーはまだ何も投稿していません。",
                header = {
                    Column {
                        ProfileHeader(
                            user = user,
                            followersCount = profileState.followersCount,
                            onOpenFollowers = { onOpenFollowers(user.sub) },
                            onOpenFollowing = { onOpenFollowing(user.sub) },
                            actions = {
                                if (isSelf) {
                                    FujuSecondaryButton(text = "プロフィール編集", onClick = onOpenEdit)
                                } else if (profileState.followStateKnown) {
                                    FollowButton(
                                        following = profileState.following,
                                        onToggle = onToggleFollow,
                                        pending = profileState.followPending,
                                        disabled = !canLike,
                                    )
                                }
                            },
                        )
                        val err = profileState.error
                        if (err != null) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = FujuDimens.SpaceL),
                            )
                        }
                    }
                },
            )
        }
    }
}
