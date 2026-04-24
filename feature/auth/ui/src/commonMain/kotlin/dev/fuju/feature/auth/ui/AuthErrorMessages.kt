package dev.fuju.feature.auth.ui

import dev.fuju.core.error.AuthException
import dev.fuju.core.error.ErrorCode

/**
 * [ErrorCode] をユーザー向け日本語メッセージに変換するローカライザ。
 * `../auth-component/` の English 版メッセージを日本語化して踏襲。
 */
object AuthErrorMessages {
    @Suppress("CyclomaticComplexMethod")
    fun toMessage(e: AuthException?): String? {
        if (e == null) return null
        return when (e.code) {
            ErrorCode.INVALID_CREDENTIALS -> "メールアドレスかパスワードが正しくありません。"
            ErrorCode.USER_ALREADY_EXISTS -> "すでに登録済みのユーザーです。"
            ErrorCode.USER_NOT_FOUND -> "ユーザーが見つかりませんでした。"
            ErrorCode.ACCOUNT_LOCKED -> "アカウントがロックされています。時間を置いて再試行してください。"
            ErrorCode.EMAIL_NOT_VERIFIED -> "メールアドレスが確認されていません。受信箱を確認してください。"
            ErrorCode.PUBLIC_ID_ALREADY_EXISTS -> "指定したユーザー ID はすでに使われています。"
            ErrorCode.PUBLIC_ID_RESERVED -> "指定したユーザー ID は利用できません。"
            ErrorCode.PUBLIC_ID_FORMAT_INVALID -> "ユーザー ID は 4〜16 文字の英数字で指定してください。"
            ErrorCode.EMAIL_INVALID -> "メールアドレスの形式が正しくありません。"
            ErrorCode.PASSWORD_TOO_SHORT -> "パスワードは 6 文字以上で指定してください。"
            ErrorCode.TOKEN_EXPIRED, ErrorCode.TOKEN_INVALID,
            ErrorCode.TOKEN_REVOKED, ErrorCode.TOKEN_MALFORMED,
            -> "セッションが無効になりました。再ログインしてください。"
            ErrorCode.MFA_REQUIRED -> "MFA 認証が必要です。"
            ErrorCode.MFA_NOT_ENABLED -> "MFA が有効になっていません。"
            ErrorCode.MFA_ALREADY_ENABLED -> "MFA はすでに有効です。"
            ErrorCode.TOTP_CODE_INVALID -> "MFA コードが正しくありません。"
            ErrorCode.RECOVERY_CODE_INVALID -> "リカバリコードが正しくありません。"
            ErrorCode.CLIENT_INVALID, ErrorCode.CLIENT_NOT_FOUND -> "クライアント設定に問題があります。"
            ErrorCode.SOCIAL_PROVIDER_INVALID, ErrorCode.SOCIAL_AUTH_FAILED -> "ソーシャル認証に失敗しました。"
            ErrorCode.INVALID_REQUEST, ErrorCode.MISSING_REQUIRED_FIELD -> "入力内容に誤りがあります。"
            ErrorCode.RATE_LIMIT_EXCEEDED -> {
                val sec = e.retryAfterSec
                if (sec != null) "リクエストが多すぎます。$sec 秒後に再試行してください。" else "リクエストが多すぎます。時間を置いて再試行してください。"
            }
            ErrorCode.FILE_TOO_LARGE -> "ファイルサイズが大きすぎます。"
            ErrorCode.FILE_FORMAT_INVALID -> "ファイル形式がサポートされていません。"
            ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.SERVICE_UNAVAILABLE,
            ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.NOT_IMPLEMENTED,
            -> "サーバーでエラーが発生しました。"
            ErrorCode.NETWORK_ERROR -> "ネットワークに接続できませんでした。"
            ErrorCode.UNKNOWN -> e.message ?: "不明なエラーが発生しました。"
        }
    }
}
