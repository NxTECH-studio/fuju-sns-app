package dev.fuju.feature.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.UpdateBadgeInput
import dev.fuju.core.ui.components.EmptyState
import dev.fuju.core.ui.components.ErrorFallback
import dev.fuju.core.ui.components.FujuPrimaryButton
import dev.fuju.core.ui.components.FujuSecondaryButton
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.feature.admin.domain.AdminBadgesViewModel
import dev.fuju.feature.admin.domain.sanitizeAdminError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 管理者「バッジマスター」画面。
 * frontend `../frontend/src/routes/admin/AdminBadgesRoute.tsx` を写経。
 *
 * - 一覧 + 新規作成フォーム + 編集ダイアログ + 削除確認
 * - isAdmin でないユーザーが到達した場合は呼び出し側で GlobalTimeline へ replace navigate
 */
@Composable
fun AdminBadgesScreen(
    viewModel: AdminBadgesViewModel,
    onOpenUserBadges: () -> Unit,
    onGrantToUser: (badgeKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Badge?>(null) }
    var deleting by remember { mutableStateOf<Badge?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var formBusy by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Admin / バッジ",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            FujuPrimaryButton(
                text = if (creating) "閉じる" else "新規作成",
                onClick = {
                    creating = !creating
                    formError = null
                },
            )
        }

        if (creating) {
            BadgeForm(
                initial = null,
                requireKey = true,
                submitLabel = "作成",
                busy = formBusy,
                error = formError,
                onCancel = {
                    creating = false
                    formError = null
                },
                onSubmit = { values ->
                    formBusy = true
                    formError = null
                    scope.launch {
                        try {
                            viewModel.create(
                                CreateBadgeInput(
                                    key = values.key,
                                    label = values.label,
                                    description = values.description.orEmpty(),
                                    iconUrl = values.iconUrl.orEmpty(),
                                    color = values.color,
                                    priority = values.priority,
                                ),
                            )
                            creating = false
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            formError = sanitizeAdminError(t)
                        } finally {
                            formBusy = false
                        }
                    }
                },
            )
        }

        when {
            state.loading && state.badges.isEmpty() ->
                Box(
                    modifier = Modifier.fillMaxWidth().padding(FujuDimens.SpaceXL),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            state.error != null && state.badges.isEmpty() ->
                ErrorFallback(
                    title = "バッジ一覧の取得に失敗しました",
                    message = state.error ?: "エラー",
                    onRetry = viewModel::reload,
                )
            state.badges.isEmpty() ->
                EmptyState(
                    title = "バッジはまだありません",
                    description = "「新規作成」から最初のバッジを登録してください。",
                )
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
                ) {
                    items(state.badges, key = { it.id }) { badge ->
                        BadgeRow(
                            badge = badge,
                            onEdit = { editing = badge },
                            onDelete = { deleting = badge },
                            onGrant = { onGrantToUser(badge.key) },
                        )
                        HorizontalDivider()
                    }
                }
        }

        FujuSecondaryButton(
            text = "ユーザーへのバッジ管理 →",
            onClick = onOpenUserBadges,
        )
    }

    val target = editing
    if (target != null) {
        BadgeEditDialog(
            badge = target,
            busy = formBusy,
            error = formError,
            onDismiss = {
                editing = null
                formError = null
            },
            onSubmit = { values ->
                formBusy = true
                formError = null
                scope.launch {
                    try {
                        viewModel.update(
                            id = target.id,
                            input =
                                UpdateBadgeInput(
                                    label = values.label,
                                    description = values.description?.takeIf { it.isNotEmpty() },
                                    iconUrl = values.iconUrl?.takeIf { it.isNotEmpty() },
                                    color = values.color,
                                    priority = values.priority,
                                ),
                        )
                        editing = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        formError = sanitizeAdminError(t)
                    } finally {
                        formBusy = false
                    }
                }
            },
        )
    }

    val toDelete = deleting
    if (toDelete != null) {
        // 削除リクエスト中はボタンを無効化し連打による重複 DELETE 発火を防ぐ。
        var deleteBusy by remember(toDelete.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!deleteBusy) deleting = null },
            title = { Text("バッジを削除しますか?") },
            text = { Text("「${toDelete.label}」(${toDelete.key}) を削除します。元に戻せません。") },
            confirmButton = {
                FujuPrimaryButton(
                    text = if (deleteBusy) "削除中..." else "削除",
                    loading = deleteBusy,
                    enabled = !deleteBusy,
                    onClick = {
                        deleteBusy = true
                        scope.launch {
                            try {
                                viewModel.delete(toDelete.id)
                            } finally {
                                deleting = null
                            }
                        }
                    },
                )
            },
            dismissButton = {
                FujuSecondaryButton(
                    text = "キャンセル",
                    enabled = !deleteBusy,
                    onClick = { deleting = null },
                )
            },
        )
    }
}

@Composable
private fun BadgeRow(
    badge: Badge,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = FujuDimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceXS),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
        ) {
            BadgeChip(badge = badge)
            Text(
                text = "key: ${badge.key} / priority: ${badge.priority}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        if (badge.description.isNotBlank()) {
            Text(badge.description, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS)) {
            FujuSecondaryButton(text = "編集", onClick = onEdit)
            FujuSecondaryButton(text = "ユーザーに付与", onClick = onGrant)
            FujuSecondaryButton(text = "削除", onClick = onDelete)
        }
    }
}

@Composable
private fun BadgeEditDialog(
    badge: Badge,
    busy: Boolean,
    error: String?,
    onSubmit: (BadgeFormValues) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("バッジを編集") },
        text = {
            BadgeForm(
                initial = badge,
                requireKey = false,
                submitLabel = "保存",
                busy = busy,
                error = error,
                onCancel = onDismiss,
                onSubmit = onSubmit,
                inDialog = true,
            )
        },
        // BadgeForm 内に submit / cancel ボタンを持っているので外側ボタンは出さない。
        confirmButton = {},
        dismissButton = {},
    )
}

/** Badge 一覧で使う最小チップ。frontend `BadgeChip.tsx` 相当。 */
@Composable
internal fun BadgeChip(badge: Badge) {
    Surface(
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(FujuDimens.RadiusPill),
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
