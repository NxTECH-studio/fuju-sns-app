package dev.fuju.feature.profile.domain

import dev.fuju.core.error.AuthException

/**
 * Profile / follow 系の操作失敗を UI 向け固定メッセージに正規化するヘルパ。
 *
 * `TimelineErrorMessages.sanitizeError` と同じ方針で status code ベースの分岐に寄せる。
 * `AuthException.message` には backend 由来の raw な説明文が入ることがあり、それをそのまま
 * UI に出さないためにここで変換する。
 */
internal fun sanitizeError(t: Throwable): String {
    val status = (t as? AuthException)?.status ?: -1
    return when {
        status == 400 -> "入力内容を確認してください。"
        status == 401 -> "認証が切れました。再度ログインしてください。"
        status == 403 -> "この操作は許可されていません。"
        status == 404 -> "ユーザーが見つかりませんでした。"
        status == 429 -> "リクエストが多すぎます。少し待ってから再試行してください。"
        status in 500..599 -> "サーバーで問題が発生しています。時間を置いて再度お試しください。"
        t is AuthException -> "通信エラーが発生しました。"
        else -> "エラーが発生しました。"
    }
}
