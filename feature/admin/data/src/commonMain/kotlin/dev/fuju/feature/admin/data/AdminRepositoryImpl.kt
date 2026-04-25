package dev.fuju.feature.admin.data

import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.ProfileUser
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
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Backend `/v1/admin/badges` 系と `/v1/admin/users/{sub}/badges` 系を叩く Repository 実装。
 * React 版 `../frontend/src/api/endpoints/admin.ts` を移植。
 *
 * ## Backend スキーマの注意点（`backend/docs/swagger.yaml` 参照）
 * - 一覧 `GET /v1/admin/badges` は `BadgeListEnvelope { data: [...] }` でエンベロープあり
 * - 詳細 `POST` / `PUT /v1/admin/badges/{id}` は `BadgeEnvelope { data: ... }`
 * - 更新は **PUT**（PATCH ではない）
 * - 付与 `POST /v1/admin/users/{sub}/badges` は `GrantBadgeEnvelope { data: { badge, ... } }`
 * - 剥奪は path に **badge_id (ULID)** を取る。`badge_key` ではない
 * - `DELETE /v1/admin/badges/{id}` は backend に未実装（本実装でも提供しない）
 *
 * ## URL encoding
 * `id` / `sub` / `badgeId` は path injection を防ぐため `encodeURLPathPart()` でエスケープする。
 * `ProfileRepositoryImpl` / `TimelineRepositoryImpl` と同じ方針。
 *
 * ## ユーザー一覧
 * Admin の検索画面用に `/users?limit=N&offset=0` を叩く。`ProfileRepositoryImpl.listUsers` と
 * 同じ `UserListResponse` スキーマを期待する。
 */
class AdminRepositoryImpl(
    private val client: HttpClient,
) : AdminRepository {
    override suspend fun listBadges(): List<Badge> =
        wrap {
            val res: BadgeListEnvelopeDto =
                client
                    .get("/v1/admin/badges")
                    .also { it.throwIfError() }
                    .body()
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

    override suspend fun listUsers(
        limit: Int,
        offset: Int,
    ): List<ProfileUser> =
        wrap {
            val res: AdminUserListResponseDto =
                client
                    .get("/users") {
                        parameter("limit", limit)
                        parameter("offset", offset)
                    }.also { it.throwIfError() }
                    .body()
            res.data.map { it.toDomain() }
        }

    private inline fun <T> wrap(block: () -> T): T = wrapAsAuthException(block)
}

// ---- DTOs (backend swagger -> KMP) ----

@Serializable
internal data class CreateBadgeDto(
    val key: String,
    val label: String,
    val description: String,
    @SerialName("icon_url") val iconUrl: String,
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

@Serializable
internal data class BadgeEnvelopeDto(
    val data: BadgeDto,
)

@Serializable
internal data class BadgeListEnvelopeDto(
    val data: List<BadgeDto>,
)

@Serializable
internal data class GrantBadgeResultDto(
    val status: String,
    @SerialName("user_id") val userId: String,
    val badge: BadgeDto,
)

@Serializable
internal data class GrantBadgeEnvelopeDto(
    val data: GrantBadgeResultDto,
)

@Serializable
internal data class AdminPublicUserDto(
    val sub: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("display_id") val displayId: String,
    @SerialName("icon_url") val iconUrl: String,
    val bio: String = "",
    @SerialName("banner_url") val bannerUrl: String = "",
    val badges: List<BadgeDto> = emptyList(),
    @SerialName("created_at") val createdAt: String,
    @SerialName("profile_refreshed_at") val profileRefreshedAt: String,
) {
    fun toDomain(): ProfileUser =
        ProfileUser(
            sub = sub,
            displayName = displayName,
            displayId = displayId,
            iconUrl = iconUrl,
            bio = bio,
            bannerUrl = bannerUrl,
            badges = badges.map { it.toDomain() },
            createdAt = createdAt,
            profileRefreshedAt = profileRefreshedAt,
        )
}

@Serializable
internal data class AdminUserListResponseDto(
    val data: List<AdminPublicUserDto>,
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
)
