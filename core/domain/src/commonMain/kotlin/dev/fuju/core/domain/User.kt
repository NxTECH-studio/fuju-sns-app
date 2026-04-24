package dev.fuju.core.domain

import kotlinx.serialization.Serializable

/**
 * AuthCore が管理するユーザー。`../auth-component/src/types.ts` の `User` を移植。
 *
 * **immutable** であり、フィールド更新は `copy` で新インスタンスを作る。React 版の
 * `readonly` 修飾子をそのまま Kotlin の `val` として表現する。
 */
@Serializable
data class User(
    val id: String,
    val publicId: String,
    val displayName: String,
    val email: String,
    val iconUrl: String?,
    val mfaEnabled: Boolean,
    val mfaVerified: Boolean,
    val linkedProviders: List<SocialProvider>,
    val createdAt: String,
)
