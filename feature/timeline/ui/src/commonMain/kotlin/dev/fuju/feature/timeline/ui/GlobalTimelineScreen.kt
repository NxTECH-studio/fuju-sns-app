package dev.fuju.feature.timeline.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.fuju.core.domain.Post
import dev.fuju.core.telemetry.TelemetryDispatcher
import dev.fuju.feature.timeline.domain.TimelineViewModel

/**
 * Global タイムライン画面。Home と構造は同じ。ViewModel の [TimelineKind] が違うだけ。
 */
@Composable
fun GlobalTimelineScreen(
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
        emptyTitle = "公開投稿がありません",
        emptyDescription = "最初の投稿者になってみましょう。",
        telemetryDispatcher = telemetryDispatcher,
    )
}
