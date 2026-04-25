package dev.fuju.feature.admin.domain

import dev.fuju.core.error.AuthException

/**
 * Admin 系操作の失敗を UI 向け固定メッセージに正規化するヘルパ。
 *
 * `TimelineErrorMessages.sanitizeError` / `ProfileErrorMessages.sanitizeError` と同じ方針で、
 * status code ベースの分岐に寄せる。`AuthException.message` には backend 由来の生の説明文が
 * 入ることがあり、それをそのまま UI に出さないために変換する。
 *
 * Admin 固有の文言:
 * - 403 は「Admin 権限がない」という運用上のシグナルなので個別文言に
 * - 409 は badge key 重複や grant 重複で出る
 */
object AdminErrorMessages {
    fun sanitizeError(t: Throwable): String {
        val status = (t as? AuthException)?.status ?: -1
        return when {
            status == 400 -> "入力内容を確認してください。"
            status == 401 -> "認証が切れました。再度ログインしてください。"
            status == 403 -> "管理者権限がありません。"
            status == 404 -> "対象が見つかりませんでした。"
            status == 409 -> "重複しています。既存の値を確認してください。"
            status == 429 -> "リクエストが多すぎます。少し待ってから再試行してください。"
            status in 500..599 -> "サーバーで問題が発生しています。時間を置いて再度お試しください。"
            t is AuthException -> "通信エラーが発生しました。"
            else -> "エラーが発生しました。"
        }
    }
}
