package dev.fuju.feature.timeline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fuju.core.domain.Post
import dev.fuju.core.telemetry.TelemetryDispatcher
import dev.fuju.core.ui.components.EmptyState
import dev.fuju.core.ui.components.ErrorFallback
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.feature.timeline.domain.PagedList

/**
 * Home / Global / User どれでも使える汎用 timeline 画面。
 * React 版 `HomeTimelineRoute.tsx` / `GlobalTimelineRoute.tsx` / `UserProfileRoute.tsx` の
 * タイムライン部分に相当する。
 *
 * - 末尾到達を `rememberLazyListState` から検出 → [onLoadMore] を呼ぶ
 * - Pull-to-refresh は Material3 `PullToRefreshBox`
 * - 初回ロード中 / エラー / 空 / 正常 で分岐表示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    state: PagedList<Post>,
    canLike: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenAuthor: (Post) -> Unit,
    onReply: (Post) -> Unit,
    onToggleLike: (Post) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    emptyTitle: String = "まだ投稿がありません",
    emptyDescription: String = "誰かをフォローするか、Global タイムラインをのぞいてみてください。",
    /**
     * Telemetry sink for view_*/scroll_stop. Pass null on routes
     * that should not emit (e.g. screenshot tests, public previews
     * before auth completes).
     */
    telemetryDispatcher: TelemetryDispatcher? = null,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.loading && state.items.isNotEmpty(),
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            state.isInitialLoading -> LoadingContent()
            state.error != null && state.items.isEmpty() ->
                ErrorFallback(
                    title = "取得に失敗しました",
                    message = state.error ?: "エラー",
                    onRetry = onRefresh,
                )
            state.isEmpty ->
                Column(modifier = Modifier.fillMaxSize()) {
                    header?.invoke()
                    EmptyState(title = emptyTitle, description = emptyDescription)
                }
            else ->
                TimelineList(
                    state = state,
                    canLike = canLike,
                    onLoadMore = onLoadMore,
                    onOpenPost = onOpenPost,
                    onOpenAuthor = onOpenAuthor,
                    onReply = onReply,
                    onToggleLike = onToggleLike,
                    header = header,
                    telemetryDispatcher = telemetryDispatcher,
                )
        }
    }
}

@Composable
private fun TimelineList(
    state: PagedList<Post>,
    canLike: Boolean,
    onLoadMore: () -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenAuthor: (Post) -> Unit,
    onReply: (Post) -> Unit,
    onToggleLike: (Post) -> Unit,
    header: (@Composable () -> Unit)?,
    telemetryDispatcher: TelemetryDispatcher?,
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
    // Impression telemetry. The header occupies index 0 when present
    // (key="timeline-header"); the trailing loading-more / end / error
    // sentinels live at indices > items.size. itemKeyAt skips them.
    val items = state.items
    val hasHeader = header != null
    if (telemetryDispatcher != null) {
        ImpressionTracker(
            listState = listState,
            dispatcher = telemetryDispatcher,
            itemKeyAt = { idx ->
                val postIdx = if (hasHeader) idx - 1 else idx
                items.getOrNull(postIdx)?.id
            },
        )
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        header?.let { slot ->
            item(key = "timeline-header") {
                Box(modifier = Modifier.fillMaxWidth()) {
                    slot()
                }
            }
        }
        items(state.items, key = { it.id }) { post ->
            PostRow(
                post = post,
                canLike = canLike,
                onOpen = { onOpenPost(post) },
                onOpenAuthor = { onOpenAuthor(post) },
                onReply = { onReply(post) },
                onToggleLike = { onToggleLike(post) },
            )
        }
        if (state.loadingMore) {
            item(key = "timeline-loading-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(FujuDimens.SpaceL),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(FujuDimens.SpaceS))
                }
            }
        } else if (state.nextCursor == null && state.items.isNotEmpty()) {
            item(key = "timeline-end") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(FujuDimens.SpaceL),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceXS),
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
            item(key = "timeline-error") {
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
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// 末尾からこの件数以内にスクロールしたら追加ロードを発火する。
private const val LOAD_MORE_THRESHOLD = 4
