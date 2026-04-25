package dev.fuju.feature.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.UpdateBadgeInput
import dev.fuju.core.ui.components.FujuPrimaryButton
import dev.fuju.core.ui.components.FujuSecondaryButton
import dev.fuju.core.ui.components.FujuTextField
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.feature.admin.domain.AdminErrorMessages
import dev.fuju.feature.admin.domain.AdminViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Badge 1 件の編集 / 新規作成画面。React 版 `BadgeForm` 相当。
 *
 * - `badgeId == null` ならば新規作成モード（key 入力可、create を叩く）
 * - `badgeId != null` ならば更新モード（key は immutable、update を叩く）
 *
 * Backend `UpdateBadgeRequest` は `key` を更新できないので、UI 側でも編集モードでは
 * key 入力を read-only にしている。
 *
 * ViewModel は `state` の購読 + `createBadge` / `updateBadge` の呼び出しに使う。
 * 完了で [onSave] が呼ばれ、呼び出し側で `popBackStack` する想定。
 */
@Composable
fun AdminBadgeEditScreen(
    viewModel: AdminViewModel,
    badgeId: String?,
    onSave: (Badge) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val existing = badgeId?.let { id -> state.badges.firstOrNull { it.id == id } }

    when {
        // 編集モードで対象が見つからない（reload 待ち or 直接遷移）→ ロード中表示
        badgeId != null && existing == null && state.badgesLoading ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        else ->
            BadgeEditForm(
                existing = existing,
                onCreate = { input -> viewModel.createBadge(input) },
                onUpdate = { id, input -> viewModel.updateBadge(id, input) },
                onSave = onSave,
                onCancel = onCancel,
                modifier = modifier,
            )
    }
}

/**
 * 編集フォーム本体。ViewModel には触れず、外から渡された [onCreate] / [onUpdate] を
 * suspend で呼ぶ。
 */
@Composable
private fun BadgeEditForm(
    existing: Badge?,
    onCreate: suspend (CreateBadgeInput) -> Badge,
    onUpdate: suspend (String, UpdateBadgeInput) -> Badge,
    onSave: (Badge) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seedKey = existing?.id.orEmpty()
    var key by rememberSaveable(seedKey) { mutableStateOf(existing?.key.orEmpty()) }
    var label by rememberSaveable(seedKey) { mutableStateOf(existing?.label.orEmpty()) }
    var description by rememberSaveable(seedKey) {
        mutableStateOf(existing?.description.orEmpty())
    }
    var iconUrl by rememberSaveable(seedKey) { mutableStateOf(existing?.iconUrl.orEmpty()) }
    var color by rememberSaveable(seedKey) {
        mutableStateOf(existing?.color.orEmpty().ifEmpty { DEFAULT_COLOR })
    }
    var priority by rememberSaveable(seedKey) {
        mutableStateOf(existing?.priority?.toString() ?: "0")
    }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 編集モードで途中から existing が降ってきた場合の seed 同期。
    LaunchedEffect(existing?.id, existing?.label, existing?.priority) {
        if (existing != null && !busy) {
            key = existing.key
            label = existing.label
            description = existing.description
            iconUrl = existing.iconUrl
            color = existing.color
            priority = existing.priority.toString()
        }
    }

    val isEditing = existing != null
    val priorityInt = priority.toIntOrNull()
    val keyValid = isEditing || (key.isNotBlank() && key.length <= KEY_MAX_LEN)
    val labelValid = label.isNotBlank() && label.length <= LABEL_MAX_LEN
    val priorityValid = priorityInt != null && priorityInt >= 0
    val canSubmit = !busy && keyValid && labelValid && priorityValid

    val scroll = rememberScrollState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
    ) {
        Text(
            text = if (isEditing) "バッジ編集" else "バッジ新規作成",
            style = MaterialTheme.typography.headlineSmall,
        )
        FujuTextField(
            label = if (isEditing) "key (編集不可)" else "key (英数 + ハイフン推奨)",
            value = key,
            onValueChange = { next -> if (!isEditing && next.length <= KEY_MAX_LEN) key = next },
            enabled = !busy && !isEditing,
        )
        FujuTextField(
            label = "label (表示名)",
            value = label,
            onValueChange = { next -> if (next.length <= LABEL_MAX_LEN) label = next },
            enabled = !busy,
        )
        FujuTextField(
            label = "description (任意)",
            value = description,
            onValueChange = { description = it },
            singleLine = false,
            enabled = !busy,
        )
        FujuTextField(
            label = "icon_url (任意)",
            value = iconUrl,
            onValueChange = { next -> if (next.length <= ICON_MAX_LEN) iconUrl = next },
            placeholder = "https://...",
            keyboardType = KeyboardType.Uri,
            enabled = !busy,
        )
        FujuTextField(
            label = "color (例: #FFAA00)",
            value = color,
            onValueChange = { next -> if (next.length <= COLOR_MAX_LEN) color = next },
            enabled = !busy,
        )
        FujuTextField(
            label = "priority (整数, 0 以上。降順で表示)",
            value = priority,
            onValueChange = { next ->
                // 数字のみ受け付ける。空欄も許容して再入力可能にする。
                if (next.isEmpty() || next.all { it.isDigit() }) priority = next
            },
            keyboardType = KeyboardType.Number,
            enabled = !busy,
            error = if (priority.isNotEmpty() && !priorityValid) "0 以上の整数を入力" else null,
        )
        if (localError != null) {
            Text(
                text = localError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS, Alignment.End),
        ) {
            FujuSecondaryButton(text = "キャンセル", onClick = onCancel, enabled = !busy)
            FujuPrimaryButton(
                text = if (busy) "保存中..." else "保存",
                loading = busy,
                enabled = canSubmit,
                onClick = {
                    localError = null
                    busy = true
                    scope.launch {
                        try {
                            val saved =
                                if (isEditing) {
                                    onUpdate(
                                        existing!!.id,
                                        UpdateBadgeInput(
                                            label = label,
                                            description = description,
                                            iconUrl = iconUrl,
                                            color = color,
                                            priority = priorityInt,
                                        ),
                                    )
                                } else {
                                    onCreate(
                                        CreateBadgeInput(
                                            key = key,
                                            label = label,
                                            description = description,
                                            iconUrl = iconUrl,
                                            color = color,
                                            priority = priorityInt ?: 0,
                                        ),
                                    )
                                }
                            onSave(saved)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            localError = AdminErrorMessages.sanitizeError(t)
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }
}

private const val DEFAULT_COLOR = "#888888"
private const val KEY_MAX_LEN = 64
private const val LABEL_MAX_LEN = 64
private const val ICON_MAX_LEN = 1024
private const val COLOR_MAX_LEN = 16
