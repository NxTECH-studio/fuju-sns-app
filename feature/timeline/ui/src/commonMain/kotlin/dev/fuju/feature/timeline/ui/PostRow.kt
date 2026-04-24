package dev.fuju.feature.timeline.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.fuju.core.domain.OGPPreview
import dev.fuju.core.domain.Post
import dev.fuju.core.ui.theme.FujuDimens

/**
 * Timeline 行。React 版 `../frontend/src/ui/components/PostCard.tsx` + `PostRow.tsx` を統合。
 *
 * 構造: アバター | (ヘッダ: 表示名 + @handle + 時刻) / 本文 / 画像グリッド / OGP / アクション
 *
 * - 画像は 1〜4 枚に応じて 1/2 列グリッド
 * - Repost は backend に未実装なので React 版と同じく reply (💬) + like (❤) のみ
 * - canLike が false の時（未認証等）は like ボタンを disable
 */
@Composable
fun PostRow(
    post: Post,
    canLike: Boolean,
    onOpen: () -> Unit,
    onOpenAuthor: () -> Unit,
    onReply: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
    showReplyMeta: Boolean = false,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
    ) {
        PostRowHeader(post = post, onOpenAuthor = onOpenAuthor, showReplyMeta = showReplyMeta)
        Text(
            text = post.content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (post.images.isNotEmpty()) {
            PostImageGrid(imageUrls = post.images.sortedBy { it.position }.map { it.publicUrl })
        }
        post.ogpPreviews.firstOrNull()?.let { ogp ->
            OgpCard(ogp = ogp)
        }
        PostActions(
            post = post,
            canLike = canLike,
            onReply = onReply,
            onToggleLike = onToggleLike,
        )
    }
    HorizontalDivider()
}

@Composable
private fun PostRowHeader(
    post: Post,
    onOpenAuthor: () -> Unit,
    showReplyMeta: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(
            url = post.author?.iconUrl,
            onClick = onOpenAuthor,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceXXS),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = post.author?.displayName ?: "(削除されたユーザー)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpenAuthor),
                )
                post.author?.displayId?.let { handle ->
                    Text(
                        text = "@$handle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = formatTimestamp(post.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showReplyMeta && post.parentPostId != null) {
                Text(
                    text = "返信中",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun Avatar(
    url: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(FujuDimens.AvatarM)
                .clip(CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
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

@Composable
private fun PostImageGrid(imageUrls: List<String>) {
    val columns = if (imageUrls.size == 1) 1 else 2
    // LazyVerticalGrid は無限高さにはできないので、画像 1 枚あたりの最大高さを
    // aspectRatio で制約し、grid 自体の高さはコンテンツに追従させる。
    val rows = (imageUrls.size + columns - 1) / columns
    val rowHeight = 180.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = rowHeight * rows + FujuDimens.SpaceS * (rows - 1).coerceAtLeast(0)),
        horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
        userScrollEnabled = false,
    ) {
        items(imageUrls) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (columns == 1) 16f / 9f else 1f)
                        .clip(RoundedCornerShape(FujuDimens.RadiusM)),
            )
        }
    }
}

@Composable
private fun OgpCard(ogp: OGPPreview) {
    Surface(
        shape = RoundedCornerShape(FujuDimens.RadiusM),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(FujuDimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceXS),
        ) {
            if (ogp.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = ogp.imageUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(FujuDimens.RadiusS)),
                )
            }
            if (ogp.siteName.isNotBlank()) {
                Text(
                    text = ogp.siteName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = ogp.title.ifBlank { ogp.url },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (ogp.description.isNotBlank()) {
                Text(
                    text = ogp.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PostActions(
    post: Post,
    canLike: Boolean,
    onReply: () -> Unit,
    onToggleLike: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceL),
    ) {
        TextButton(onClick = onReply) {
            Text("💬 ${post.repliesCount}")
        }
        TextButton(onClick = onToggleLike, enabled = canLike) {
            val heart = if (post.likedByViewer) "❤" else "♡"
            val color = if (post.likedByViewer) MaterialTheme.colorScheme.error else Color.Unspecified
            Text(
                text = "$heart ${post.likesCount}",
                color = color,
            )
        }
    }
}

/**
 * `2026-04-24T10:15:30Z` のような ISO 8601 を `2026-04-24 10:15` 程度に整形する最小実装。
 * KMP 共通で扱えるよう kotlinx-datetime を使わず、文字列分割のみで済ませる。
 * 細かい相対時刻（`3h ago` 等）は別タスクで導入する想定。
 */
internal fun formatTimestamp(iso: String): String {
    // `yyyy-MM-ddTHH:mm:ss(.fff)?Z?` 形式を想定
    val tIndex = iso.indexOf('T')
    if (tIndex <= 0) return iso
    val date = iso.substring(0, tIndex)
    val timePart = iso.substring(tIndex + 1)
    val hhmm = timePart.take(5)
    if (hhmm.length < 5) return iso
    return "$date $hhmm"
}
