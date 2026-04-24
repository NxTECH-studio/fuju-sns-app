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
import dev.fuju.core.domain.User
import dev.fuju.core.domain.Validators
import dev.fuju.core.error.AuthException
import dev.fuju.core.error.ErrorCode
import dev.fuju.core.ui.components.FujuPrimaryButton
import dev.fuju.core.ui.components.FujuSecondaryButton
import dev.fuju.core.ui.components.FujuTextField
import dev.fuju.core.ui.theme.FujuDimens
import kotlinx.coroutines.launch

@Composable
fun RegisterForm(
    onRegister: suspend (email: String, password: String, publicId: String) -> User,
    onSuccess: (User) -> Unit = {},
    onLoginClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var publicId by remember { mutableStateOf("") }
    var emailErr by remember { mutableStateOf<ErrorCode?>(null) }
    var pwErr by remember { mutableStateOf<ErrorCode?>(null) }
    var idErr by remember { mutableStateOf<ErrorCode?>(null) }
    var serverErr by remember { mutableStateOf<AuthException?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
    ) {
        Text(text = "新規登録", style = MaterialTheme.typography.displayMedium)
        FujuTextField(
            label = "メールアドレス",
            value = email,
            onValueChange = {
                email = it
                emailErr = null
            },
            keyboardType = KeyboardType.Email,
            enabled = !loading,
            error = if (emailErr == ErrorCode.EMAIL_INVALID) "メールアドレスの形式が正しくありません。" else null,
            modifier = Modifier.fillMaxWidth(),
        )
        FujuTextField(
            label = "パスワード（6 文字以上）",
            value = password,
            onValueChange = {
                password = it
                pwErr = null
            },
            isPassword = true,
            enabled = !loading,
            error = if (pwErr == ErrorCode.PASSWORD_TOO_SHORT) "パスワードは 6 文字以上です。" else null,
            modifier = Modifier.fillMaxWidth(),
        )
        FujuTextField(
            label = "ユーザー ID（4〜16 文字の英数字）",
            value = publicId,
            onValueChange = {
                publicId = it
                idErr = null
            },
            enabled = !loading,
            error = when (idErr) {
                ErrorCode.PUBLIC_ID_RESERVED -> "指定した ID は利用できません。"
                ErrorCode.PUBLIC_ID_FORMAT_INVALID -> "4〜16 文字の英数字で指定してください。"
                else -> null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        val serverMessage = AuthErrorMessages.toMessage(serverErr)
        if (serverMessage != null) {
            Text(
                text = serverMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        FujuPrimaryButton(
            text = "登録する",
            loading = loading,
            enabled = email.isNotBlank() && password.isNotBlank() && publicId.isNotBlank(),
            onClick = {
                emailErr = Validators.validateEmail(email)
                pwErr = Validators.validatePassword(password)
                idErr = Validators.validatePublicId(publicId)
                if (emailErr != null || pwErr != null || idErr != null) return@FujuPrimaryButton
                scope.launch {
                    loading = true
                    serverErr = null
                    try {
                        val user = onRegister(email, password, publicId)
                        onSuccess(user)
                    } catch (e: AuthException) {
                        serverErr = e
                    } finally {
                        loading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (onLoginClick != null) {
            FujuSecondaryButton(
                text = "ログイン画面へ戻る",
                onClick = onLoginClick,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
