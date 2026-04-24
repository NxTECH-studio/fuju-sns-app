package dev.fuju.core.domain

/**
 * `login()` の戻り値。`../auth-component/src/types.ts` の `LoginResult` を移植。
 * - パスワード正解 & MFA 未設定 → [Authenticated]
 * - パスワード正解 & MFA 設定済 → [MFARequired]（pre_token 取得済み）
 */
sealed class LoginResult {
    data class Authenticated(val user: User) : LoginResult()
    data object MFARequired : LoginResult()
}

/**
 * MFA 登録初期化のレスポンス。AuthCore `POST /v1/auth/mfa/register` から得られる
 * 秘密鍵 + QR コード (data URL) + recovery code 群。
 */
data class MFASetupResult(
    val secret: String,
    val qrCodeDataURL: String,
    val recoveryCodes: List<String>,
)
