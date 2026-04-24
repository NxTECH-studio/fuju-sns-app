package dev.fuju.feature.timeline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fuju.core.domain.Post
import dev.fuju.core.ui.theme.FujuDimens

/**
 * 縦並びの投稿一覧。ページング UI (下部スクロール時の追加ロード) は後段で別途。
 */
@Composable
fun TimelineList(
    posts: List<Post>,
    modifier: Modifier = Modifier,
    onPostClick: (Post) -> Unit = {},
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(posts, key = { it.id }) { post ->
            PostCard(post = post, onClick = { onPostClick(post) })
            HorizontalDivider()
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
    ) {
        Text(
            text = post.author?.displayName ?: "@${post.userId}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(text = post.content, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "♥ ${post.likesCount}   💬 ${post.repliesCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
