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
import dev.fuju.core.domain.LoginResult
import dev.fuju.core.domain.SocialProvider
import dev.fuju.core.domain.Validators
import dev.fuju.core.error.AuthException
import dev.fuju.core.error.ErrorCode
import dev.fuju.core.ui.components.FujuPrimaryButton
import dev.fuju.core.ui.components.FujuSecondaryButton
import dev.fuju.core.ui.components.FujuTextField
import dev.fuju.core.ui.theme.FujuDimens
import kotlinx.coroutines.launch

/**
 * `../auth-component/src/components/LoginForm.tsx` の KMP 版。
 *
 * - identifier (email or publicId) + password
 * - Submit ボタン
 * - Provider ボタン（`providers` 指定時のみ）
 * - Register 画面へのリンク（コールバックが渡されたとき）
 */
@Composable
fun LoginForm(
    onLogin: suspend (identifier: String, password: String) -> LoginResult,
    onLoginWithSocial: (SocialProvider) -> Unit,
    modifier: Modifier = Modifier,
    providers: List<SocialProvider> = emptyList(),
    onRegisterClick: (() -> Unit)? = null,
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<AuthException?>(null) }
    var clientError by remember { mutableStateOf<ErrorCode?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
    ) {
        Text(
            text = "ログイン",
            style = MaterialTheme.typography.displayMedium,
        )
        FujuTextField(
            label = "メールアドレス または ユーザー ID",
            value = identifier,
            onValueChange = {
                identifier = it
                clientError = null
            },
            keyboardType = KeyboardType.Email,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
        FujuTextField(
            label = "パスワード",
            value = password,
            onValueChange = {
                password = it
                clientError = null
            },
            isPassword = true,
            enabled = !loading,
            error = if (clientError == ErrorCode.PASSWORD_TOO_SHORT) "パスワードは 6 文字以上です。" else null,
            modifier = Modifier.fillMaxWidth(),
        )
        val serverMessage = AuthErrorMessages.toMessage(error)
        if (serverMessage != null) {
            Text(
                text = serverMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        FujuPrimaryButton(
            text = "ログイン",
            loading = loading,
            enabled = identifier.isNotBlank() && password.isNotBlank(),
            onClick = {
                val pwErr = Validators.validatePassword(password)
                if (pwErr != null) {
                    clientError = pwErr
                    return@FujuPrimaryButton
                }
                scope.launch {
                    loading = true
                    error = null
                    try {
                        onLogin(identifier, password)
                    } catch (e: AuthException) {
                        error = e
                    } finally {
                        loading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        providers.forEach { provider ->
            FujuSecondaryButton(
                text = "${providerLabel(provider)}でログイン",
                onClick = { onLoginWithSocial(provider) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (onRegisterClick != null) {
            FujuSecondaryButton(
                text = "新規登録はこちら",
                onClick = onRegisterClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            )
        }
    }
}

internal fun providerLabel(provider: SocialProvider): String =
    when (provider) {
        SocialProvider.GOOGLE -> "Google"
        SocialProvider.TWITCH -> "Twitch"
        SocialProvider.X -> "X"
    }
