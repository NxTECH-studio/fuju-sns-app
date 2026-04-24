package dev.fuju.core.error

/**
 * AuthCore / Backend 由来のエラー。HTTP status + code + retry-after を保持する。
 *
 * React 版 `../auth-component/src/isAuthError.ts` の `AuthError` に相当。Kotlin では
 * `Throwable` として投げ、呼び出し側が `when (status)` や `when (code)` で分岐する。
 */
class AuthException(
    val code: ErrorCode,
    val status: Int,
    message: String,
    val retryAfterSec: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    companion object {
        /** network blip 相当の未知エラーを [ErrorCode.NETWORK_ERROR] + status=0 で包む。 */
        fun network(cause: Throwable?): AuthException =
            AuthException(
                code = ErrorCode.NETWORK_ERROR,
                status = 0,
                message = cause?.message ?: "network error",
                cause = cause,
            )

        fun from(status: Int, wireCode: String?, message: String?, retryAfterSec: Int? = null): AuthException =
            AuthException(
                code = ErrorCode.fromWireOrUnknown(wireCode),
                status = status,
                message = message ?: "request failed",
                retryAfterSec = retryAfterSec,
            )
    }
}

/**
 * 任意の throwable を [AuthException] に正規化する。
 * 既に [AuthException] ならそのまま、そうでなければ [AuthException.network] にラップ。
 */
fun Throwable.toAuthException(): AuthException =
    when (this) {
        is AuthException -> this
        else -> AuthException.network(this)
    }
