package dev.fuju.feature.timeline.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.fuju.core.domain.Post
import dev.fuju.core.telemetry.TelemetryDispatcher
import dev.fuju.feature.timeline.domain.TimelineViewModel

/**
 * Home タイムライン画面。
 * React 版 `HomeTimelineRoute.tsx` と同じく ComposerBox はシェルのグローバル FAB 経由で
 * 起動する前提（画面内にインライン表示しない）。
 */
@Composable
fun HomeTimelineScreen(
    viewModel: TimelineViewModel,
    canLike: Boolean,
    onOpenPost: (Post) -> Unit,
    onOpenAuthor: (Post) -> Unit,
    onReply: (Post) -> Unit,
    modifier: Modifier = Modifier,
    telemetryDispatcher: TelemetryDispatcher? = null,
) {
    val state by viewModel.state.collectAsState()
    TimelineScreen(
        state = state,
        canLike = canLike,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onOpenPost = onOpenPost,
        onOpenAuthor = onOpenAuthor,
        onReply = onReply,
        onToggleLike = viewModel::toggleLike,
        modifier = modifier,
        emptyTitle = "まだ投稿がありません",
        emptyDescription = "誰かをフォローするか、Global タイムラインをのぞいてみてください。",
        telemetryDispatcher = telemetryDispatcher,
    )
}
