package dev.fuju.core.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AuthCore が対応しているソーシャル認証プロバイダ。
 *
 * `../auth-component/src/types.ts` の `SocialProvider` ユニオン型に対応する。
 * JSON では文字列 (google / twitch / x) として (de)serialize する。
 */
@Serializable
enum class SocialProvider {
    @SerialName("google")
    GOOGLE,

    @SerialName("twitch")
    TWITCH,

    @SerialName("x")
    X,
    ;

    /** `fuju://auth/callback/:provider` 等の URL path 部分に埋める。 */
    val slug: String
        get() =
            when (this) {
                GOOGLE -> "google"
                TWITCH -> "twitch"
                X -> "x"
            }

    companion object {
        fun fromSlug(value: String): SocialProvider? = entries.firstOrNull { it.slug == value.lowercase() }
    }
}
