package dev.fuju.feature.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import dev.fuju.core.domain.AuthSnapshot
import dev.fuju.core.domain.AuthStatus

/**
 * `../auth-component/src/AuthGuard.tsx` の KMP 版。
 *
 * - [required] == true のとき、`Authenticated` 以外は fallback を表示する。
 * - [enforceMFA] == true のとき、MFA を有効にしていないユーザーには [mfaSetup] を表示する。
 * - 未認証なら [onRequireLogin] を呼び出して呼び出し側のナビゲーションに委譲する。
 *
 * AuthGuard は 条件分岐で content / mfaSetup を出し分けるため `movableContentOf` で包んで
 * state を保持する。React 版にあった `loadingFallback` 等は呼び出し側の責務に寄せる。
 */
@Composable
fun AuthGuard(
    snapshot: AuthSnapshot,
    onRequireLogin: () -> Unit,
    required: Boolean = true,
    enforceMFA: Boolean = false,
    mfaSetup: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val movableContent = remember(content) { movableContentOf(content) }
    val status = snapshot.status
    if (!required) {
        movableContent()
        return
    }
    when (status) {
        AuthStatus.Authenticated -> {
            val user = snapshot.user
            if (enforceMFA && user != null && !user.mfaEnabled) {
                mfaSetup()
            } else {
                movableContent()
            }
        }
        AuthStatus.Unauthenticated -> onRequireLogin()
        AuthStatus.MFARequired,
        AuthStatus.Idle,
        AuthStatus.Authenticating,
        AuthStatus.Error,
        -> {
            // 呼び出し側で loading / error 表示を重ねるために content は出さない
        }
    }
}
