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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fuju.core.error.AuthException
import dev.fuju.core.ui.theme.FujuDimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * テキストのみの投稿コンポーザ。画像アップロードは別タスクで expect/actual を切る。
 * React 版 `ComposerBox.tsx` の最小相当。Reply にも再利用する。
 *
 * @param parentHint 返信先の表示用ヒント。`null` なら新規投稿扱い。
 * @param onSubmit 成功時は dialog を閉じる。例外は [errorMessage] に反映される。
 *                 ダイアログを途中で閉じると進行中の submit はキャンセルされる。
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
    var submitJob by remember { mutableStateOf<Job?>(null) }

    // Composable が消える時に pending submit も確実に止める。
    // 通常は scope 自体が cancel されるので二重だが、parent 側が scope を長寿命で
    // 持ち回している時の保険。
    DisposableEffect(Unit) {
        onDispose { submitJob?.cancel() }
    }

    val canSubmit = content.isNotBlank() && content.length <= MAX_CONTENT_LENGTH && !busy

    val cancelPendingAndDismiss = {
        submitJob?.cancel()
        submitJob = null
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) cancelPendingAndDismiss() },
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
                    submitJob =
                        scope.launch {
                            try {
                                onSubmit(snapshot)
                                onDismiss()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (t: Throwable) {
                                errorMessage = sanitizeComposerError(t)
                            } finally {
                                busy = false
                                submitJob = null
                            }
                        }
                },
            ) {
                Text(if (parentHint != null) "返信" else "投稿")
            }
        },
        dismissButton = {
            TextButton(onClick = cancelPendingAndDismiss, enabled = !busy) { Text("キャンセル") }
        },
    )
}

// Backend の `CreatePostRequest.content` maxLength に合わせる（120 文字）。
private const val MAX_CONTENT_LENGTH = 120

/**
 * ComposerDialog から見せるエラー文言。backend 由来の生メッセージを流さず status ベースで固定化する。
 */
private fun sanitizeComposerError(t: Throwable): String {
    val status = (t as? AuthException)?.status ?: -1
    return when {
        status == 401 -> "認証が切れました。再度ログインしてください。"
        status == 403 -> "この操作は許可されていません。"
        status == 413 -> "本文が長すぎます。"
        status == 429 -> "投稿が多すぎます。少し待ってから再試行してください。"
        status in 400..499 -> "入力内容を確認してください。"
        status in 500..599 -> "サーバーで問題が発生しています。時間を置いて再度お試しください。"
        else -> "投稿に失敗しました。"
    }
}
