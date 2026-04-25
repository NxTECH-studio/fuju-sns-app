package dev.fuju.feature.admin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fuju.core.domain.Badge
import dev.fuju.core.ui.components.EmptyState
import dev.fuju.core.ui.components.ErrorFallback
import dev.fuju.core.ui.components.FujuSecondaryButton
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.feature.admin.domain.AdminState
import dev.fuju.feature.admin.domain.AdminViewModel

/**
 * Admin タブの "バッジマスタ一覧" 画面。React 版 `AdminBadgesRoute.tsx` 相当。
 *
 * - Badge 一覧は priority 降順 (ViewModel が並び替え済み)
 * - 行タップで [onEditBadge] (id 指定で編集画面)
 * - FAB で [onCreateBadge] (id=null で新規作成画面)
 * - 「ユーザーへのバッジ管理」ボタンで [onOpenUserManagement]
 *
 * ViewModel はこの 1 箇所だけで触り、下位 Composable には plain 型と callback を渡す
 * ("hoist all the things")。
 */
@Composable
fun AdminBadgesScreen(
    viewModel: AdminViewModel,
    onCreateBadge: () -> Unit,
    onEditBadge: (Badge) -> Unit,
    onOpenUserManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    AdminBadgesContent(
        state = state,
        onReload = viewModel::reloadBadges,
        onCreateBadge = onCreateBadge,
        onEditBadge = onEditBadge,
        onOpenUserManagement = onOpenUserManagement,
        modifier = modifier,
    )
}

@Composable
private fun AdminBadgesContent(
    state: AdminState,
    onReload: () -> Unit,
    onCreateBadge: () -> Unit,
    onEditBadge: (Badge) -> Unit,
    onOpenUserManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateBadge,
                text = { Text("新規作成") },
                icon = { Text("＋", style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(FujuDimens.SpaceL),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("バッジマスタ", style = MaterialTheme.typography.titleLarge)
                FujuSecondaryButton(
                    text = "ユーザーへの付与 →",
                    onClick = onOpenUserManagement,
                )
            }
            when {
                state.badgesLoading && state.badges.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.badges.isEmpty() && state.error != null ->
                    ErrorFallback(
                        title = "バッジを取得できませんでした",
                        message = state.error ?: "",
                        onRetry = onReload,
                        modifier = Modifier.fillMaxSize(),
                    )
                state.badges.isEmpty() ->
                    EmptyState(
                        title = "バッジはまだありません",
                        description = "FAB から新規作成してください。",
                    )
                else ->
                    AdminBadgesList(
                        badges = state.badges,
                        onClick = onEditBadge,
                    )
            }
        }
    }
}

/**
 * Badge 一覧の本体。タップで編集画面へ遷移する。
 */
@Composable
internal fun AdminBadgesList(
    badges: List<Badge>,
    onClick: (Badge) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(badges, key = { it.id }) { badge ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onClick(badge) }
                        .padding(FujuDimens.SpaceL),
                verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceXS),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(badge.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "priority ${badge.priority}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "#${badge.key}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (badge.description.isNotBlank()) {
                    Text(badge.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider()
        }
    }
}
