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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.ui.components.EmptyState
import dev.fuju.core.ui.components.ErrorFallback
import dev.fuju.core.ui.components.FujuPrimaryButton
import dev.fuju.core.ui.components.FujuSecondaryButton
import dev.fuju.core.ui.components.FujuTextField
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.feature.admin.domain.AdminErrorMessages
import dev.fuju.feature.admin.domain.AdminViewModel

/**
 * 選択ユーザーへのバッジ付与 / 剥奪画面。React 版 `AdminUserBadgesRoute.tsx` の
 * userCard + 現在のバッジ + バッジ付与 セクションを移植。
 *
 * - 現在のバッジは [user] の `badges` をそのまま表示。Optimistic に剥奪 / 付与で更新する
 * - badge_key は ViewModel が持つマスタ一覧 (`state.badges`) からの選択 + 自由入力
 * - reason は任意。空ならば `null` で送信
 *
 * `userSub` は path 経由で受け取り、ViewModel.users から該当ユーザーを引く。
 * 見つからない場合は ErrorFallback で reload を促す。
 */
@Composable
fun AdminUserBadgesScreen(
    viewModel: AdminViewModel,
    userSub: String,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val initialUser = state.users.firstOrNull { it.sub == userSub }
    // grant / revoke の結果で badges を optimistic に書き換える: ViewModel.users 自体は
    // 全件を持つので per-user の更新だけ local state に持つ。
    var localBadges by remember(initialUser?.sub) {
        mutableStateOf<List<Badge>?>(initialUser?.badges)
    }
    val effectiveBadges = localBadges ?: initialUser?.badges.orEmpty()

    when {
        initialUser == null && state.usersLoading ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        initialUser == null ->
            ErrorFallback(
                title = "ユーザーが見つかりませんでした",
                message = state.error ?: "ユーザー一覧を再取得してください。",
                onRetry = viewModel::reloadUsers,
                modifier = modifier.fillMaxSize(),
            )
        else ->
            UserBadgesContent(
                user = initialUser,
                userBadges = effectiveBadges,
                masterBadges = state.badges,
                onGrant = { input, onResult ->
                    viewModel.grantBadge(initialUser.sub, input) { result ->
                        if (result.isSuccess) {
                            val granted = result.getOrThrow()
                            // 既に同じ id があれば置換、無ければ追加。
                            localBadges =
                                effectiveBadges.let { current ->
                                    if (current.any { it.id == granted.id }) {
                                        current.map { if (it.id == granted.id) granted else it }
                                    } else {
                                        current + granted
                                    }
                                }
                        }
                        onResult(result)
                    }
                },
                onRevoke = { badgeId, onResult ->
                    val before = effectiveBadges
                    // Optimistic: 即時に該当を取り除く。失敗したら戻す。
                    localBadges = before.filter { it.id != badgeId }
                    viewModel.revokeBadge(initialUser.sub, badgeId) { result ->
                        if (result.isFailure) localBadges = before
                        onResult(result)
                    }
                },
                modifier = modifier,
            )
    }
}

@Composable
private fun UserBadgesContent(
    user: ProfileUser,
    userBadges: List<Badge>,
    masterBadges: List<Badge>,
    onGrant: (GrantBadgeInput, (Result<Badge>) -> Unit) -> Unit,
    onRevoke: (String, (Result<Unit>) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceL),
    ) {
        // ユーザーカード
        Column(verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceXS)) {
            Text(user.displayName, style = MaterialTheme.typography.titleLarge)
            Text(
                "@${user.displayId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                user.sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        // 現在のバッジ
        Column(verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM)) {
            Text("現在のバッジ", style = MaterialTheme.typography.titleMedium)
            if (userBadges.isEmpty()) {
                EmptyState(title = "バッジなし", description = "下のフォームから付与できます。")
            } else {
                CurrentBadgesList(badges = userBadges, onRevoke = onRevoke)
            }
        }

        HorizontalDivider()

        // バッジ付与フォーム
        GrantForm(masterBadges = masterBadges, onGrant = onGrant)
    }
}

@Composable
private fun CurrentBadgesList(
    badges: List<Badge>,
    onRevoke: (String, (Result<Unit>) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRevokeId by remember { mutableStateOf<String?>(null) }
    var revokeError by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(badges, key = { it.id }) { badge ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = FujuDimens.SpaceS),
                    horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(badge.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "#${badge.key}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FujuSecondaryButton(
                        text = if (pendingRevokeId == badge.id) "剥奪中..." else "剥奪",
                        enabled = pendingRevokeId == null,
                        onClick = {
                            revokeError = null
                            pendingRevokeId = badge.id
                            onRevoke(badge.id) { result ->
                                pendingRevokeId = null
                                if (result.isFailure) {
                                    val cause = result.exceptionOrNull() ?: RuntimeException()
                                    revokeError = AdminErrorMessages.sanitizeError(cause)
                                }
                            }
                        },
                    )
                }
                HorizontalDivider()
            }
        }
        if (revokeError != null) {
            Text(
                text = revokeError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = FujuDimens.SpaceS),
            )
        }
    }
}

@Composable
private fun GrantForm(
    masterBadges: List<Badge>,
    onGrant: (GrantBadgeInput, (Result<Badge>) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var badgeKey by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
    ) {
        Text("バッジを付与", style = MaterialTheme.typography.titleMedium)
        if (masterBadges.isNotEmpty()) {
            Text(
                text = "候補: ${masterBadges.joinToString(limit = 8) { it.key }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FujuTextField(
            label = "badge_key",
            value = badgeKey,
            onValueChange = { badgeKey = it },
            placeholder = "supporter",
            enabled = !busy,
        )
        FujuTextField(
            label = "reason (任意)",
            value = reason,
            onValueChange = { next -> if (next.length <= REASON_MAX_LEN) reason = next },
            singleLine = false,
            enabled = !busy,
        )
        if (localError != null) {
            Text(
                text = localError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (success != null) {
            Text(
                text = success!!,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FujuPrimaryButton(
            text = if (busy) "付与中..." else "付与",
            loading = busy,
            enabled = !busy && badgeKey.isNotBlank(),
            onClick = {
                localError = null
                success = null
                busy = true
                val input =
                    GrantBadgeInput(
                        badgeKey = badgeKey.trim(),
                        reason = reason.takeIf { it.isNotBlank() },
                    )
                onGrant(input) { result ->
                    busy = false
                    if (result.isSuccess) {
                        success = "付与しました"
                        badgeKey = ""
                        reason = ""
                    } else {
                        val cause = result.exceptionOrNull() ?: RuntimeException()
                        localError = AdminErrorMessages.sanitizeError(cause)
                    }
                }
            },
        )
    }
}

private const val REASON_MAX_LEN = 255
