package dev.fuju.feature.admin.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.ui.components.ErrorFallback
import dev.fuju.core.ui.components.FujuPrimaryButton
import dev.fuju.core.ui.components.FujuSecondaryButton
import dev.fuju.core.ui.components.FujuTextField
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.feature.admin.domain.AdminUserBadgesViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 管理者「ユーザーへのバッジ管理」画面。
 * frontend `../frontend/src/routes/admin/AdminUserBadgesRoute.tsx` を写経。
 *
 * 構造:
 * - 上段: フィルタ + 前/次ページ + ユーザー一覧（クリックで対象選択）
 * - 中段: sub 直接入力で対象指定
 * - 下段: 選択中ユーザーの badges 一覧 + 付与フォーム
 *
 * filter は frontend と同じく **現在のページ内** での部分一致のみ。ページ全体検索は別タスク。
 */
@Composable
fun AdminUserBadgesScreen(
    viewModel: AdminUserBadgesViewModel,
    initialBadgeKey: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf("") }
    var subInput by remember { mutableStateOf("") }
    var badgeKey by remember(initialBadgeKey) { mutableStateOf(initialBadgeKey) }
    var reason by remember { mutableStateOf("") }
    var revokeTarget by remember { mutableStateOf<RevokeTarget?>(null) }

    val filteredUsers by remember(state.users, filter) {
        derivedStateOf {
            val needle = filter.trim().lowercase()
            if (needle.isEmpty()) {
                state.users
            } else {
                state.users.filter {
                    it.displayName.lowercase().contains(needle) ||
                        it.displayId.lowercase().contains(needle) ||
                        it.sub.lowercase().contains(needle)
                }
            }
        }
    }
    val filterActive = filter.isNotBlank()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
    ) {
        item("header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
            ) {
                FujuSecondaryButton(text = "← バッジマスター", onClick = onBack)
                Text(
                    text = "ユーザーへのバッジ管理",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        item("picker-controls") {
            Column(verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS)) {
                FujuTextField(
                    label = "フィルタ (displayName / @id / sub の部分一致)",
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = "検索...",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS)) {
                    FujuSecondaryButton(
                        text = "前へ",
                        onClick = { viewModel.prevPage() },
                        enabled = state.hasPrev && !state.usersLoading && !filterActive,
                    )
                    FujuSecondaryButton(
                        text = "次へ",
                        onClick = { viewModel.nextPage() },
                        enabled = state.hasNext && !state.usersLoading && !filterActive,
                    )
                    Text(
                        text =
                            if (state.total > 0) {
                                "${state.offset + 1}-${state.offset + state.users.size} / ${state.total}"
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = FujuDimens.SpaceS).align(Alignment.CenterVertically),
                    )
                }
                if (filterActive) {
                    Text(
                        text = "フィルタは現在のページ内のみに適用されます。別ページを見るにはフィルタをクリアしてください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        when {
            state.usersError != null && state.users.isEmpty() ->
                item("users-error") {
                    ErrorFallback(
                        title = "ユーザー一覧の取得に失敗しました",
                        message = state.usersError ?: "エラー",
                        onRetry = { viewModel.loadUsers(state.offset) },
                    )
                }
            state.usersLoading && state.users.isEmpty() ->
                item("users-loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(FujuDimens.SpaceXL),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            filteredUsers.isEmpty() ->
                item("users-empty") {
                    Text(
                        text = if (state.usersLoading) "読み込み中..." else "該当ユーザーなし",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else ->
                items(filteredUsers, key = { it.sub }) { user ->
                    UserRow(
                        user = user,
                        onClick = { viewModel.selectTarget(user.sub) },
                        selected = state.targetUser?.sub == user.sub,
                    )
                    HorizontalDivider()
                }
        }
        item("sub-lookup") {
            Column(verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS)) {
                FujuTextField(
                    label = "sub を直接指定 (ULID, 26 文字)",
                    value = subInput,
                    onValueChange = { subInput = it },
                    placeholder = "01HZXYABCDEFGHJKMNPQRSTVWX",
                )
                FujuPrimaryButton(
                    text = "読み込み",
                    onClick = {
                        if (subInput.trim().length == 26) {
                            viewModel.selectTarget(subInput.trim())
                        }
                    },
                    enabled = subInput.trim().length == 26 && !state.targetLoading,
                )
            }
        }
        val target = state.targetUser
        if (state.targetLoading && target == null) {
            item("target-loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(FujuDimens.SpaceL),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        if (state.targetError != null && target == null) {
            item("target-error") {
                ErrorFallback(
                    title = "ユーザーの取得に失敗しました",
                    message = state.targetError ?: "エラー",
                    onRetry = { viewModel.clearTargetError() },
                )
            }
        }
        if (target != null) {
            item("target-card") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape =
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(FujuDimens.RadiusM),
                ) {
                    Row(
                        modifier = Modifier.padding(FujuDimens.SpaceM),
                        horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(url = target.iconUrl)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(target.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "@${target.displayId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = target.sub,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FujuSecondaryButton(text = "解除", onClick = { viewModel.clearTarget() })
                    }
                }
            }
            item("target-badges-heading") {
                Text("現在のバッジ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (target.badges.isEmpty()) {
                item("target-badges-empty") {
                    Text(
                        "なし",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(target.badges, key = { it.id }) { badge ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = FujuDimens.SpaceXS),
                        horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BadgeChip(badge = badge)
                        Text(
                            text = "key: ${badge.key}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        FujuSecondaryButton(
                            text = "剥奪",
                            onClick = { revokeTarget = RevokeTarget(badgeId = badge.id, label = badge.label) },
                        )
                    }
                }
            }
            item("grant-form") {
                Column(verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS)) {
                    Text("バッジを付与", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FujuTextField(
                        label = "badge_key",
                        value = badgeKey,
                        onValueChange = { badgeKey = it },
                        enabled = !state.grantPending,
                    )
                    FujuTextField(
                        label = "reason (任意, 最大 255 文字)",
                        value = reason,
                        onValueChange = { if (it.length <= 255) reason = it },
                        enabled = !state.grantPending,
                    )
                    val grantError = state.grantError
                    if (grantError != null) {
                        Text(
                            text = grantError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    FujuPrimaryButton(
                        text = if (state.grantPending) "付与中..." else "付与",
                        loading = state.grantPending,
                        enabled = badgeKey.isNotBlank() && !state.grantPending,
                        onClick = {
                            scope.launch {
                                try {
                                    viewModel.grant(
                                        GrantBadgeInput(
                                            badgeKey = badgeKey.trim(),
                                            reason = reason.takeIf { it.isNotBlank() },
                                        ),
                                    )
                                    badgeKey = ""
                                    reason = ""
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Throwable) {
                                    // grantError が state に反映されているのでここでは追加処理なし。
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    val pendingRevoke = revokeTarget
    if (pendingRevoke != null) {
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("バッジを剥奪しますか?") },
            text = { Text("「${pendingRevoke.label}」をこのユーザーから剥奪します。") },
            confirmButton = {
                FujuPrimaryButton(
                    text = "剥奪",
                    onClick = {
                        scope.launch {
                            try {
                                viewModel.revoke(pendingRevoke.badgeId)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Throwable) {
                                // grantError 反映済み
                            } finally {
                                revokeTarget = null
                            }
                        }
                    },
                )
            },
            dismissButton = {
                FujuSecondaryButton(text = "キャンセル", onClick = { revokeTarget = null })
            },
        )
    }
}

private data class RevokeTarget(
    val badgeId: String,
    val label: String,
)

@Composable
private fun UserRow(
    user: ProfileUser,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg =
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
    ) {
        Surface(color = bg, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(FujuDimens.SpaceS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
            ) {
                Avatar(url = user.iconUrl)
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "@${user.displayId} · ${user.sub}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Avatar(
    url: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(FujuDimens.AvatarS)
                .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.size(FujuDimens.AvatarS).clip(CircleShape),
            )
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
                Box(modifier = Modifier.size(FujuDimens.AvatarS), contentAlignment = Alignment.Center) {
                    Text("?", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
