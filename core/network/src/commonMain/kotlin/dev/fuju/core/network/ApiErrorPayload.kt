package dev.fuju.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Backend / AuthCore 共通の error body スキーマ。
 * ```json
 * { "code": "...", "message": "...", "timestamp": "..." }
 * ```
 * `../frontend/src/api/types.ts` の `ApiErrorPayload` を移植。
 */
@Serializable
data class ApiErrorPayload(
    @SerialName("code") val code: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
)
