package dev.fuju.feature.timeline.domain

import dev.fuju.core.error.AuthException

/**
 * Timeline 系の操作失敗を UI に出す固定メッセージに寄せるヘルパ。
 *
 * `AuthException.message` には backend 由来の生の説明文が入ることがあり、
 * それをそのまま UI に出すと開発者向けの文言や内部状態が流れる恐れがある。
 * ここで status code ベースの固定メッセージに正規化する。
 *
 * UI 層で再度翻訳するようにしたくなったら、state を `AuthException` 型で expose する
 * 形に変えて `sanitizeError` の呼び出しを UI 側に寄せる。
 */
internal fun sanitizeError(t: Throwable): String {
    val status = (t as? AuthException)?.status ?: -1
    return when {
        status == 401 -> "認証が切れました。再度ログインしてください。"
        status == 403 -> "この操作は許可されていません。"
        status == 404 -> "見つかりませんでした。"
        status == 429 -> "リクエストが多すぎます。少し待ってから再試行してください。"
        status in 500..599 -> "サーバーで問題が発生しています。時間を置いて再度お試しください。"
        t is AuthException -> "通信エラーが発生しました。"
        else -> "エラーが発生しました。"
    }
}
