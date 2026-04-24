package dev.fuju.core.domain

import dev.fuju.core.error.ErrorCode

/**
 * `../auth-component/src/validators/{email,password,publicId}.ts` を Kotlin に移植。
 * 成功時は `null`、失敗時は [ErrorCode] を返す同期関数群。
 */
object Validators {
    const val PASSWORD_MIN_LENGTH: Int = 6

    private val EMAIL_RE = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
    private val PUBLIC_ID_RE = Regex("""^[a-zA-Z0-9]{4,16}$""")

    fun validateEmail(value: String): ErrorCode? = if (!EMAIL_RE.matches(value)) ErrorCode.EMAIL_INVALID else null

    fun validatePassword(value: String): ErrorCode? =
        if (value.length < PASSWORD_MIN_LENGTH) ErrorCode.PASSWORD_TOO_SHORT else null

    fun validatePublicId(value: String): ErrorCode? {
        if (value.length in 1..3) return ErrorCode.PUBLIC_ID_RESERVED
        if (!PUBLIC_ID_RE.matches(value)) return ErrorCode.PUBLIC_ID_FORMAT_INVALID
        return null
    }
}
