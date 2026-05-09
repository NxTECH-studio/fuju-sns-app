package dev.fuju.feature.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dev.fuju.core.domain.Badge
import dev.fuju.core.ui.components.FujuPrimaryButton
import dev.fuju.core.ui.components.FujuSecondaryButton
import dev.fuju.core.ui.components.FujuTextField
import dev.fuju.core.ui.theme.FujuDimens

/**
 * バッジ作成 / 編集用フォーム。frontend `../frontend/src/ui/components/BadgeForm.tsx` を写経。
 *
 * - 作成時 (`requireKey = true`): key を編集可能、空文字は不可
 * - 編集時 (`requireKey = false`): key は read-only 表示
 * - color はカラーピッカーを Compose 標準で持たないので、frontend と同じく hex を text 入力
 *
 * 値の変換は呼び出し側に任せる（[BadgeFormValues] のフィールドはそのまま optional な値）。
 */
@Composable
fun BadgeForm(
    initial: Badge?,
    requireKey: Boolean,
    submitLabel: String,
    busy: Boolean,
    error: String?,
    onSubmit: (BadgeFormValues) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    inDialog: Boolean = false,
) {
    val seed = initial?.id ?: "new"
    var key by rememberSaveable(seed) { mutableStateOf(initial?.key.orEmpty()) }
    var label by rememberSaveable(seed) { mutableStateOf(initial?.label.orEmpty()) }
    var description by rememberSaveable(seed) { mutableStateOf(initial?.description.orEmpty()) }
    var iconUrl by rememberSaveable(seed) { mutableStateOf(initial?.iconUrl.orEmpty()) }
    var color by rememberSaveable(seed) { mutableStateOf(initial?.color ?: "#888888") }
    var priorityText by rememberSaveable(seed) { mutableStateOf((initial?.priority ?: 0).toString()) }

    val priorityValid = priorityText.toIntOrNull() != null
    val canSubmit =
        label.isNotBlank() &&
            color.isNotBlank() &&
            priorityValid &&
            (!requireKey || key.isNotBlank()) &&
            !busy

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
    ) {
        if (requireKey) {
            FujuTextField(
                label = "key (英数字 + アンダースコア)",
                value = key,
                onValueChange = { key = it },
                enabled = !busy,
            )
        } else if (initial != null) {
            Text(
                text = "key: ${initial.key}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FujuTextField(
            label = "label",
            value = label,
            onValueChange = { label = it },
            enabled = !busy,
        )
        FujuTextField(
            label = "description",
            value = description,
            onValueChange = { description = it },
            singleLine = false,
            enabled = !busy,
        )
        FujuTextField(
            label = "icon URL",
            value = iconUrl,
            onValueChange = { iconUrl = it },
            placeholder = "https://...",
            keyboardType = KeyboardType.Uri,
            enabled = !busy,
        )
        FujuTextField(
            label = "color (#RRGGBB)",
            value = color,
            onValueChange = { color = it },
            enabled = !busy,
        )
        FujuTextField(
            label = "priority (整数)",
            value = priorityText,
            onValueChange = { next -> if (next.all { it.isDigit() || it == '-' }) priorityText = next },
            error = if (priorityText.isNotEmpty() && !priorityValid) "整数で入力してください" else null,
            keyboardType = KeyboardType.Number,
            enabled = !busy,
        )
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS, Alignment.End),
        ) {
            FujuSecondaryButton(text = "キャンセル", onClick = onCancel, enabled = !busy)
            FujuPrimaryButton(
                text = if (busy) "送信中..." else submitLabel,
                loading = busy,
                enabled = canSubmit,
                onClick = {
                    onSubmit(
                        BadgeFormValues(
                            key = key.trim(),
                            label = label.trim(),
                            description = description.takeIf { it.isNotEmpty() },
                            iconUrl = iconUrl.takeIf { it.isNotEmpty() },
                            color = color.trim(),
                            priority = priorityText.toIntOrNull() ?: 0,
                        ),
                    )
                },
            )
        }
        // inDialog 時は外側で余白を持つので何もしない。
        @Suppress("UNUSED_EXPRESSION")
        inDialog
    }
}

/**
 * フォームから submit される生の値。空文字は呼び出し側で `null` に寄せる。
 */
data class BadgeFormValues(
    val key: String,
    val label: String,
    val description: String?,
    val iconUrl: String?,
    val color: String,
    val priority: Int,
)
