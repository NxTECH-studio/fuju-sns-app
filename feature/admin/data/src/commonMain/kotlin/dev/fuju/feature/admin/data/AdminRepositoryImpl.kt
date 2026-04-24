package dev.fuju.feature.admin.data

import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.UpdateBadgeInput
import dev.fuju.core.error.toAuthException
import dev.fuju.core.network.throwIfError
import dev.fuju.feature.admin.domain.AdminRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class AdminRepositoryImpl(private val client: HttpClient) : AdminRepository {

    override suspend fun listBadges(): List<Badge> = wrap {
        val list: List<BadgeDto> = client.get("/v1/admin/badges").also { it.throwIfError() }.body()
        list.map { it.toDomain() }
    }

    override suspend fun createBadge(input: CreateBadgeInput): Badge = wrap {
        val dto: BadgeDto = client.post("/v1/admin/badges") {
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
        }.also { it.throwIfError() }.body()
        dto.toDomain()
    }

    override suspend fun updateBadge(id: String, input: UpdateBadgeInput): Badge = wrap {
        val dto: BadgeDto = client.patch("/v1/admin/badges/$id") {
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
        }.also { it.throwIfError() }.body()
        dto.toDomain()
    }

    override suspend fun deleteBadge(id: String) {
        wrap { client.delete("/v1/admin/badges/$id").throwIfError() }
    }

    override suspend fun grantBadge(userSub: String, input: GrantBadgeInput) {
        wrap {
            client.post("/v1/admin/users/$userSub/badges") {
                contentType(ContentType.Application.Json)
                setBody(
                    GrantBadgeDto(
                        badgeKey = input.badgeKey,
                        expiresAt = input.expiresAt,
                        reason = input.reason,
                    ),
                )
            }.throwIfError()
        }
    }

    override suspend fun revokeBadge(userSub: String, badgeKey: String) {
        wrap { client.delete("/v1/admin/users/$userSub/badges/$badgeKey").throwIfError() }
    }

    private inline fun <T> wrap(block: () -> T): T =
        try {
            block()
        } catch (t: Throwable) {
            throw t.toAuthException()
        }
}

@Serializable
internal data class BadgeDto(
    val id: String,
    val key: String,
    val label: String,
    val description: String = "",
    @SerialName("icon_url") val iconUrl: String = "",
    val color: String,
    val priority: Int,
) {
    fun toDomain(): Badge = Badge(id, key, label, description, iconUrl, color, priority)
}

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
