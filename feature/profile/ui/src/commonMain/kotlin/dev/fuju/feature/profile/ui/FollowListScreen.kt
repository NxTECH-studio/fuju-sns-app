package dev.fuju.feature.profile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.ui.components.EmptyState
import dev.fuju.core.ui.components.ErrorFallback
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.feature.profile.domain.FollowListKind
import dev.fuju.feature.profile.domain.FollowListState
import dev.fuju.feature.profile.domain.FollowListViewModel

/**
 * Follower / Following 一覧画面。React 版 `routes/FollowListRoute.tsx` に相当。
 *
 * UserCard 相当の [FollowListRow] を [LazyColumn] に並べ、末尾到達で [FollowListViewModel.loadMore]
 * を呼ぶ。タップで該当ユーザーのプロフィールに遷移する。
 *
 * ViewModel はこの Composable だけで所有し、下位 Composable には state と callback を
 * plain 型で渡す（Compose Rules の "hoist all the things"）。
 */
@Composable
fun FollowListScreen(
    viewModel: FollowListViewModel,
    onOpenUser: (ProfileUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    FollowListContent(
        state = state,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onOpenUser = onOpenUser,
        modifier = modifier,
    )
}

@Composable
private fun FollowListContent(
    state: FollowListState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenUser: (ProfileUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isInitialLoading ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        state.error != null && state.items.isEmpty() ->
            ErrorFallback(
                title = "一覧を取得できませんでした",
                message = state.error ?: "エラー",
                onRetry = onRefresh,
                modifier = modifier.fillMaxSize(),
            )
        state.isEmpty ->
            EmptyState(
                title = "ユーザーがいません",
                description = "まだこの一覧には誰もいません。",
                modifier = modifier.fillMaxSize(),
            )
        else ->
            FollowListItems(
                state = state,
                onLoadMore = onLoadMore,
                onOpenUser = onOpenUser,
                modifier = modifier,
            )
    }
}

@Composable
private fun FollowListItems(
    state: FollowListState,
    onLoadMore: () -> Unit,
    onOpenUser: (ProfileUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val nearEnd by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            val total = layout.totalItemsCount
            total > 0 && last >= total - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.canLoadMore) {
        if (nearEnd && state.canLoadMore) currentOnLoadMore()
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        items(state.items, key = { it.sub }) { user ->
            FollowListRow(user = user, onClick = { onOpenUser(user) })
        }
        if (state.loadingMore) {
            item(key = "follow-list-loading-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(FujuDimens.SpaceL),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(FujuDimens.SpaceS))
                }
            }
        } else if (state.nextCursor == null && state.items.isNotEmpty()) {
            item(key = "follow-list-end") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(FujuDimens.SpaceL),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "すべて表示しました",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val err = state.error
        if (err != null && state.items.isNotEmpty()) {
            item(key = "follow-list-error") {
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(FujuDimens.SpaceL),
                )
            }
        }
    }
}

@Composable
private fun FollowListRow(
    user: ProfileUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceXS),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(url = user.iconUrl.takeIf { it.isNotBlank() })
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${user.displayId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (user.bio.isNotBlank()) {
            Text(
                text = user.bio,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun UserAvatar(
    url: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(FujuDimens.AvatarM).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.size(FujuDimens.AvatarM).clip(CircleShape),
            )
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
                Box(modifier = Modifier.size(FujuDimens.AvatarM), contentAlignment = Alignment.Center) {
                    Text("?", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** 画面タイトルを決めるためのヘルパ。NavHost 側から呼ばれる。 */
fun followListTitle(kind: FollowListKind): String =
    when (kind) {
        FollowListKind.Followers -> "フォロワー"
        FollowListKind.Following -> "フォロー中"
    }

private const val LOAD_MORE_THRESHOLD = 4
