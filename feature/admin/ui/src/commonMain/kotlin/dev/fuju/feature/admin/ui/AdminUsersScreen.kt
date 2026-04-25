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
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.ui.components.EmptyState
import dev.fuju.core.ui.components.ErrorFallback
import dev.fuju.core.ui.components.FujuTextField
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.feature.admin.domain.AdminViewModel
import dev.fuju.feature.admin.domain.filterUsers

/**
 * Admin の "ユーザー一覧 + 検索" 画面。React 版 `AdminUserBadgesRoute.tsx` の
 * picker 部分を独立画面に切り出したもの。
 *
 * - 全ユーザーは ViewModel が初期 load する（backend に search API が無いため）
 * - 検索ボックスへの入力に応じて [filterUsers] でクライアントサイドフィルタ
 * - 行タップで [onSelectUser]（呼び出し側がそのユーザーの badge 管理画面に遷移）
 *
 * ViewModel は `state` の購読 + `reloadUsers` のみで使う。grant / revoke は遷移先で行う。
 */
@Composable
fun AdminUsersScreen(
    viewModel: AdminViewModel,
    onSelectUser: (ProfileUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(state.users, query) { filterUsers(state.users, query) }

    Column(
        modifier = modifier.fillMaxSize().padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
    ) {
        Text("ユーザーを選択", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "検索は表示名 / @id / sub の部分一致 (大文字小文字無視)。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FujuTextField(
            label = "検索",
            value = query,
            onValueChange = { query = it },
            placeholder = "名前 / @id / sub",
        )
        when {
            state.usersLoading && state.users.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.users.isEmpty() && state.error != null ->
                ErrorFallback(
                    title = "ユーザーを取得できませんでした",
                    message = state.error ?: "",
                    onRetry = viewModel::reloadUsers,
                    modifier = Modifier.fillMaxSize(),
                )
            filtered.isEmpty() ->
                EmptyState(
                    title = if (query.isBlank()) "ユーザーがいません" else "該当ユーザーなし",
                    description = if (query.isBlank()) "" else "検索条件を見直してください。",
                )
            else ->
                AdminUserList(
                    users = filtered,
                    onSelect = onSelectUser,
                )
        }
    }
}

@Composable
private fun AdminUserList(
    users: List<ProfileUser>,
    onSelect: (ProfileUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(users, key = { it.sub }) { user ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(user) }
                        .padding(FujuDimens.SpaceL),
                verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceXS),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "@${user.displayId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = user.sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (user.badges.isNotEmpty()) {
                    Text(
                        text = "バッジ: ${user.badges.joinToString { it.label }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}
