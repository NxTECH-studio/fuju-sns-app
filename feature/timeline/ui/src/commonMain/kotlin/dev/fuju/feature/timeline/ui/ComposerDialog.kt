package dev.fuju.feature.timeline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fuju.core.ui.theme.FujuDimens
import kotlinx.coroutines.launch

/**
 * テキストのみの投稿コンポーザ。画像アップロードは別タスクで expect/actual を切る。
 * React 版 `ComposerBox.tsx` の最小相当。Reply にも再利用する。
 *
 * @param parentHint 返信先の表示用ヒント。`null` なら新規投稿扱い。
 * @param onSubmit 成功時は dialog を閉じる。例外は [errorMessage] に反映される。
 */
@Composable
fun ComposerDialog(
    onDismiss: () -> Unit,
    onSubmit: suspend (content: String) -> Unit,
    parentHint: String? = null,
) {
    var content by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val canSubmit = content.isNotBlank() && content.length <= MAX_CONTENT_LENGTH && !busy

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(if (parentHint != null) "返信を書く" else "新しい投稿")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (parentHint != null) {
                    Text(
                        text = "返信先: $parentHint",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { next -> if (next.length <= MAX_CONTENT_LENGTH) content = next },
                    label = { Text("本文") },
                    supportingText = { Text("${content.length} / $MAX_CONTENT_LENGTH") },
                    isError = errorMessage != null,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                val err = errorMessage
                if (err != null) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    val snapshot = content
                    errorMessage = null
                    busy = true
                    scope.launch {
                        try {
                            onSubmit(snapshot)
                            onDismiss()
                        } catch (t: Throwable) {
                            errorMessage = t.message ?: "投稿に失敗しました"
                        } finally {
                            busy = false
                        }
                    }
                },
            ) {
                Text(if (parentHint != null) "返信" else "投稿")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("キャンセル") }
        },
    )
}

// Backend の `CreatePostRequest.content` maxLength に合わせる（120 文字）。
private const val MAX_CONTENT_LENGTH = 120
