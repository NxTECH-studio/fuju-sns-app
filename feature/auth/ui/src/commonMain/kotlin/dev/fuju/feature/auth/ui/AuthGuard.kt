package dev.fuju.feature.auth.ui

import androidx.compose.runtime.Composable
import dev.fuju.core.domain.AuthSnapshot
import dev.fuju.core.domain.AuthStatus

/**
 * `../auth-component/src/AuthGuard.tsx` の KMP 版。
 *
 * - [required] == true のとき、`Authenticated` 以外は [fallback] を表示する。
 * - [enforceMFA] == true のとき、MFA を有効にしていないユーザーには [mfaSetup] を表示する。
 * - 未認証なら [onUnauthenticated] を呼び出して呼び出し側のナビゲーションに委譲する。
 *
 * React 版にあった `loadingFallback` 等は呼び出し側の責務に寄せる。
 */
@Composable
fun AuthGuard(
    snapshot: AuthSnapshot,
    required: Boolean = true,
    enforceMFA: Boolean = false,
    onUnauthenticated: () -> Unit,
    mfaSetup: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val status = snapshot.status
    if (!required) {
        content()
        return
    }
    when (status) {
        AuthStatus.Authenticated -> {
            val user = snapshot.user
            if (enforceMFA && user != null && !user.mfaEnabled) {
                mfaSetup()
            } else {
                content()
            }
        }
        AuthStatus.Unauthenticated -> onUnauthenticated()
        AuthStatus.MFARequired,
        AuthStatus.Idle,
        AuthStatus.Authenticating,
        AuthStatus.Error -> {
            // 呼び出し側で loading / error 表示を重ねるために content は出さない
        }
    }
}
