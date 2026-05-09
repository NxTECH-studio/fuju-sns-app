package dev.fuju.feature.admin.data

import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.UpdateBadgeInput
import dev.fuju.core.network.dto.BadgeDto
import dev.fuju.core.network.throwIfError
import dev.fuju.core.network.throwIfErrorOrDiscard
import dev.fuju.core.network.wrapAsAuthException
import dev.fuju.feature.admin.domain.AdminRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Backend `/v1/admin/badges` 系を叩く実装。frontend `../frontend/src/api/endpoints/admin.ts`
 * を移植。
 *
 * envelope 構造に注意:
 * - list: `{ data: Badge[] }`
 * - create / update: `{ data: Badge }`
 * - grant: `{ data: { status, user_id, badge: Badge } }` → badge を取り出す
 * - revoke / delete: 204 で空応答
 *
 * REST verb は frontend と揃える: 更新は **PUT**（旧実装は PATCH だった）。
 */
class AdminRepositoryImpl(
    private val client: HttpClient,
) : AdminRepository {
    override suspend fun listBadges(): List<Badge> =
        wrap {
            val res: BadgeListEnvelopeDto =
                client.get("/v1/admin/badges").also { it.throwIfError() }.body()
            res.data.map { it.toDomain() }
        }

    override suspend fun createBadge(input: CreateBadgeInput): Badge =
        wrap {
            val res: BadgeEnvelopeDto =
                client
                    .post("/v1/admin/badges") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            CreateBadgeDto(
                                key = input.key,
                                label = input.label,
                                description = input.description.takeIf { it.isNotEmpty() },
                                iconUrl = input.iconUrl.takeIf { it.isNotEmpty() },
                                color = input.color,
                                priority = input.priority,
                            ),
                        )
                    }.also { it.throwIfError() }
                    .body()
            res.data.toDomain()
        }

    override suspend fun updateBadge(
        id: String,
        input: UpdateBadgeInput,
    ): Badge =
        wrap {
            val res: BadgeEnvelopeDto =
                client
                    .put("/v1/admin/badges/${id.encodeURLPathPart()}") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            UpdateBadgeDto(
                                label = input.label,
                                description = input.description,
                                iconUrl = input.iconUrl,
                                color = input.color,
                                priority = input.priority,
                            ),
                        )
                    }.also { it.throwIfError() }
                    .body()
            res.data.toDomain()
        }

    override suspend fun deleteBadge(id: String) {
        wrap { client.delete("/v1/admin/badges/${id.encodeURLPathPart()}").throwIfErrorOrDiscard() }
    }

    override suspend fun grantBadge(
        userSub: String,
        input: GrantBadgeInput,
    ): Badge =
        wrap {
            val res: GrantBadgeEnvelopeDto =
                client
                    .post("/v1/admin/users/${userSub.encodeURLPathPart()}/badges") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            GrantBadgeDto(
                                badgeKey = input.badgeKey,
                                expiresAt = input.expiresAt,
                                reason = input.reason,
                            ),
                        )
                    }.also { it.throwIfError() }
                    .body()
            res.data.badge.toDomain()
        }

    override suspend fun revokeBadge(
        userSub: String,
        badgeId: String,
    ) {
        wrap {
            client
                .delete(
                    "/v1/admin/users/${userSub.encodeURLPathPart()}/badges/${badgeId.encodeURLPathPart()}",
                ).throwIfErrorOrDiscard()
        }
    }

    private inline fun <T> wrap(block: () -> T): T = wrapAsAuthException(block)
}

@Serializable
internal data class BadgeEnvelopeDto(
    val data: BadgeDto,
)

@Serializable
internal data class BadgeListEnvelopeDto(
    val data: List<BadgeDto>,
)

@Serializable
internal data class GrantBadgeEnvelopeDto(
    val data: GrantBadgePayloadDto,
)

@Serializable
internal data class GrantBadgePayloadDto(
    val status: String = "granted",
    @SerialName("user_id") val userId: String,
    val badge: BadgeDto,
)

@Serializable
internal data class CreateBadgeDto(
    val key: String,
    val label: String,
    val description: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    val color: String,
    val priority: Int,
)

@Serializable
internal data class UpdateBadgeDto(
    val label: String? = null,
    val description: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    val color: String? = null,
    val priority: Int? = null,
)

@Serializable
internal data class GrantBadgeDto(
    @SerialName("badge_key") val badgeKey: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    val reason: String? = null,
)
