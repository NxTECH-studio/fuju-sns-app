package dev.fuju.feature.timeline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fuju.core.domain.Post
import dev.fuju.core.ui.components.ErrorFallback
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.feature.timeline.domain.PostDetailViewModel

/**
 * 投稿詳細画面。親 post + 返信一覧。
 * React 版 `PostDetailRoute.tsx` を移植。返信コンポーザは別画面として呼び出し側で起動する。
 */
@Composable
fun PostDetailScreen(
    viewModel: PostDetailViewModel,
    canLike: Boolean,
    onOpenAuthor: (Post) -> Unit,
    onOpenReply: (Post) -> Unit,
    onRequestReplyComposer: (Post) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val post = state.post

    when {
        state.loadingPost && post == null ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        state.postError != null && post == null ->
            ErrorFallback(
                title = "投稿の取得に失敗しました",
                message = state.postError ?: "エラー",
                onRetry = viewModel::reload,
                modifier = modifier,
            )
        post == null ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("投稿が見つかりません", style = MaterialTheme.typography.bodyLarge)
            }
        else ->
            PostDetailContent(
                post = post,
                replies = state.replies,
                canLike = canLike,
                onOpenAuthor = onOpenAuthor,
                onOpenReply = onOpenReply,
                onToggleLike = viewModel::toggleLike,
                onLoadMoreReplies = viewModel::loadMoreReplies,
                onRequestReplyComposer = { onRequestReplyComposer(post) },
                modifier = modifier,
            )
    }
}

@Composable
private fun PostDetailContent(
    post: Post,
    replies: dev.fuju.feature.timeline.domain.PagedList<Post>,
    canLike: Boolean,
    onOpenAuthor: (Post) -> Unit,
    onOpenReply: (Post) -> Unit,
    onToggleLike: (Post) -> Unit,
    onLoadMoreReplies: () -> Unit,
    onRequestReplyComposer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentOnLoadMoreReplies by rememberUpdatedState(onLoadMoreReplies)
    val nearEnd by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            val total = layout.totalItemsCount
            total > 0 && last >= total - REPLIES_LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, replies.canLoadMore) {
        if (nearEnd && replies.canLoadMore) currentOnLoadMoreReplies()
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "primary") {
            PostRow(
                post = post,
                canLike = canLike,
                onOpen = { /* 自分自身なので no-op */ },
                onOpenAuthor = { onOpenAuthor(post) },
                onReply = onRequestReplyComposer,
                onToggleLike = { onToggleLike(post) },
            )
        }
        item(key = "replies-heading") {
            Row(modifier = Modifier.fillMaxWidth().padding(FujuDimens.SpaceL)) {
                Text(
                    text = "返信 (${post.repliesCount})",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        if (replies.items.isEmpty() && !replies.loading) {
            item(key = "replies-empty") {
                Text(
                    text = "返信はまだありません",
                    modifier = Modifier.padding(FujuDimens.SpaceL),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(replies.items, key = { it.id }) { reply ->
            PostRow(
                post = reply,
                canLike = canLike,
                onOpen = { onOpenReply(reply) },
                onOpenAuthor = { onOpenAuthor(reply) },
                onReply = onRequestReplyComposer,
                onToggleLike = { onToggleLike(reply) },
                showReplyMeta = true,
            )
        }
        if (replies.loadingMore || (replies.loading && replies.items.isNotEmpty())) {
            item(key = "replies-loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(FujuDimens.SpaceL),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(FujuDimens.SpaceS))
                }
            }
        }
        val repliesErr = replies.error
        if (repliesErr != null) {
            item(key = "replies-error") {
                Text(
                    text = repliesErr,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(FujuDimens.SpaceL),
                )
            }
        }
        item(key = "bottom-spacer") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(FujuDimens.SpaceL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
            ) {
                HorizontalDivider()
                TextButton(onClick = onRequestReplyComposer) {
                    Text("この投稿に返信する")
                }
            }
        }
    }
}

private const val REPLIES_LOAD_MORE_THRESHOLD = 4
