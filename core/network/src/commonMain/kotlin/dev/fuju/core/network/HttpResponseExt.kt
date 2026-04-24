package dev.fuju.core.network

import dev.fuju.core.error.AuthException
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.discard

/**
 * 2xx 以外なら [AuthException] に変換して throw する。
 * React 版 `../frontend/src/api/client.ts` の `throwIfError` 相当。
 */
suspend fun HttpResponse.throwIfError() {
    if (status.isSuccess()) return
    val payload = runCatching { body<ApiErrorPayload>() }.getOrNull()
    val retryAfter = headers["Retry-After"]?.toIntOrNull()
    throw AuthException.from(
        status = status.value,
        wireCode = payload?.code ?: defaultWireCode(status),
        message = payload?.message ?: status.description,
        retryAfterSec = retryAfter,
    )
}

/**
 * ボディを使わない fire-and-forget 系呼び出し用。
 * 2xx 以外なら [AuthException] を throw し、成功時はボディを discard して
 * コネクションを確実に返す。未読のままにすると OkHttp エンジンでコネクションが
 * プールに戻らず、最悪タイムアウトまで占有する。
 */
suspend fun HttpResponse.throwIfErrorOrDiscard() {
    throwIfError()
    if (status != HttpStatusCode.NoContent) {
        bodyAsChannel().discard()
    }
}

private fun defaultWireCode(status: HttpStatusCode): String = "HTTP_${status.value}"

/**
 * 2xx レスポンスの body を de-serialize する。
 * 204 No Content のときは `null` を返す（呼び出し側で `Unit` などに対応）。
 */
suspend inline fun <reified T> HttpResponse.decodeOrNull(): T? {
    if (status == HttpStatusCode.NoContent) return null
    return body<T>()
}
