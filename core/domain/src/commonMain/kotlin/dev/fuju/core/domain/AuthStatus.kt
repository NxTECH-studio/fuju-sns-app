package dev.fuju.core.domain

/**
 * Auth ライフサイクルの状態。`../auth-component/src/types.ts` の `AuthStatus`
 * ユニオン型に対応。
 *
 * React 版では string literal union だが、Kotlin では sealed class で表現して
 * `when` 式の網羅性を型で保証する。
 */
sealed class AuthStatus {
    /** 初期状態。`bootstrap()` 呼び出し前。 */
    data object Idle : AuthStatus()

    /** Bootstrap 中 / ログイン処理中 / MFA verify 中。 */
    data object Authenticating : AuthStatus()

    /** 認証済み。`User` は [AuthSnapshot.user] 側に載る。 */
    data object Authenticated : AuthStatus()

    /** MFA verify 待ち。pre_token が [AuthSnapshot.preTokenPresent] == true。 */
    data object MFARequired : AuthStatus()

    /** 未認証。bootstrap 失敗 / logout 完了 / refresh 失敗時など。 */
    data object Unauthenticated : AuthStatus()

    /** Bootstrap / refresh 系で非 auth なエラー（5xx, network）が発生したとき。 */
    data object Error : AuthStatus()
}
