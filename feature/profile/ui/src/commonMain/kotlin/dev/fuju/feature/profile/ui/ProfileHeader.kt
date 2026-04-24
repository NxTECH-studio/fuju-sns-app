package dev.fuju.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.ui.theme.FujuDimens

/**
 * プロフィール画面ヘッダ。バナー画像 → アイコン + 名前 → bio → badges → フォロー数 の縦並び。
 * React 版 `ui/components/UserProfileView.tsx` に相当する。
 *
 * follow ボタン / 編集ボタンは呼び出し側が `actions` スロットに入れる。これは "自分か他人か"
 * や "認証済みか" の判定が ViewModel 側にしか無いため、UI コンポーネント自身を単純に保つ意図。
 */
@Composable
fun ProfileHeader(
    user: ProfileUser,
    followersCount: Int?,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
    ) {
        Banner(url = user.bannerUrl.takeIf { it.isNotBlank() })
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FujuDimens.SpaceL)
                    .offset(y = -FujuDimens.SpaceXL),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
        ) {
            ProfileAvatar(url = user.iconUrl.takeIf { it.isNotBlank() })
            if (actions != null) {
                Box(modifier = Modifier.padding(bottom = FujuDimens.SpaceS)) {
                    actions()
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FujuDimens.SpaceL)
                    .offset(y = -FujuDimens.SpaceL),
            verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceXS),
        ) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "@${user.displayId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (user.bio.isNotBlank()) {
                Text(
                    text = user.bio,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = FujuDimens.SpaceXS),
                )
            }
            if (user.badges.isNotEmpty()) {
                BadgeStrip(
                    badges = user.badges,
                    modifier = Modifier.padding(top = FujuDimens.SpaceXS),
                )
            }
            FollowCounts(
                followersCount = followersCount,
                onOpenFollowers = onOpenFollowers,
                onOpenFollowing = onOpenFollowing,
                modifier = Modifier.padding(top = FujuDimens.SpaceXS),
            )
        }
    }
}

@Composable
private fun Banner(
    url: String?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(0.dp)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
        }
    }
}

@Composable
private fun ProfileAvatar(
    url: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(FujuDimens.AvatarL)
                .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.size(FujuDimens.AvatarL).clip(CircleShape),
            )
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
                Box(
                    modifier = Modifier.size(FujuDimens.AvatarL),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("?", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun BadgeStrip(
    badges: List<Badge>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceXS),
    ) {
        badges.sortedByDescending { it.priority }.take(MAX_BADGES).forEach { badge ->
            Surface(
                shape = RoundedCornerShape(FujuDimens.RadiusPill),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = badge.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier =
                        Modifier.padding(
                            horizontal = FujuDimens.SpaceS,
                            vertical = FujuDimens.SpaceXXS,
                        ),
                )
            }
        }
    }
}

@Composable
private fun FollowCounts(
    followersCount: Int?,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
    ) {
        TextButton(onClick = onOpenFollowers) {
            Text(text = "${followersCount ?: "—"} フォロワー")
        }
        TextButton(onClick = onOpenFollowing) {
            Text(text = "フォロー中")
        }
    }
}

private const val MAX_BADGES: Int = 4
