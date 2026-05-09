package dev.fuju.feature.admin.domain

import dev.fuju.core.error.AuthException

/**
 * Admin 系操作の失敗を UI 向け固定メッセージに正規化する。
 * profile / timeline と同じ status code ベースの分岐方針。
 */
fun sanitizeAdminError(t: Throwable): String {
    val status = (t as? AuthException)?.status ?: -1
    return when {
        status == 400 -> "入力内容を確認してください。"
        status == 401 -> "認証が切れました。再度ログインしてください。"
        status == 403 -> "管理者権限が必要です。"
        status == 404 -> "対象が見つかりませんでした。"
        status == 409 -> "既に登録されています。"
        status == 429 -> "リクエストが多すぎます。少し待ってから再試行してください。"
        status in 500..599 -> "サーバーで問題が発生しています。時間を置いて再度お試しください。"
        t is AuthException -> "通信エラーが発生しました。"
        else -> "エラーが発生しました。"
    }
}
