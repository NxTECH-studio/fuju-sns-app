package dev.fuju.core.domain

import dev.fuju.core.error.AuthException

/**
 * Auth state machine から観測される不変なスナップショット。
 * React 版 `AuthStore.getSnapshot()` に対応。
 *
 * UI 層は `StateFlow<AuthSnapshot>` を `collectAsState()` して購読する。
 */
data class AuthSnapshot(
    val status: AuthStatus,
    val user: User?,
    val preTokenPresent: Boolean,
    val error: AuthException?,
    val mfaAttempts: Int,
    val needsPublicIdSetup: Boolean,
) {
    companion object {
        val Initial: AuthSnapshot =
            AuthSnapshot(
                status = AuthStatus.Idle,
                user = null,
                preTokenPresent = false,
                error = null,
                mfaAttempts = 0,
                needsPublicIdSetup = false,
            )
    }
}
