package dev.fuju.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dev.fuju.core.error.AuthException
import dev.fuju.core.ui.components.FujuPrimaryButton
import dev.fuju.core.ui.components.FujuSecondaryButton
import dev.fuju.core.ui.components.FujuTextField
import dev.fuju.core.ui.theme.FujuDimens
import kotlinx.coroutines.launch

/**
 * TOTP または recovery code のどちらかを送信する MFA 検証画面。
 * React 版 `MFAChallenge` と同じ UI モデル（2 タブではなく 1 画面で切替）。
 */
@Composable
fun MFAChallenge(
    onVerify: suspend (code: String?, recoveryCode: String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    attempts: Int = 0,
) {
    var usingRecovery by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<AuthException?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
    ) {
        Text(text = "MFA 認証", style = MaterialTheme.typography.displayMedium)
        Text(
            text = if (usingRecovery) "リカバリコードを入力してください" else "認証アプリの 6 桁コードを入力してください",
            style = MaterialTheme.typography.bodyMedium,
        )
        FujuTextField(
            label = if (usingRecovery) "リカバリコード" else "TOTP コード",
            value = value,
            onValueChange = { value = it },
            keyboardType = if (usingRecovery) KeyboardType.Text else KeyboardType.Number,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
        if (attempts > 0) {
            Text(
                text = "$attempts 回失敗しました",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val message = AuthErrorMessages.toMessage(err)
        if (message != null) {
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
        FujuPrimaryButton(
            text = "認証する",
            loading = loading,
            enabled = value.isNotBlank(),
            onClick = {
                scope.launch {
                    loading = true
                    err = null
                    try {
                        if (usingRecovery) onVerify(null, value) else onVerify(value, null)
                    } catch (e: AuthException) {
                        err = e
                    } finally {
                        loading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        FujuSecondaryButton(
            text = if (usingRecovery) "TOTP コードに戻る" else "リカバリコードで認証",
            onClick = {
                usingRecovery = !usingRecovery
                value = ""
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
        FujuSecondaryButton(
            text = "キャンセル",
            onClick = onCancel,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
