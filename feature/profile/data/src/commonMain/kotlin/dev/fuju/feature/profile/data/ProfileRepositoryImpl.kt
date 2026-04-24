package dev.fuju.feature.profile.data

import dev.fuju.core.domain.FollowResult
import dev.fuju.core.domain.Me
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.domain.UpdateProfileInput
import dev.fuju.core.network.dto.BadgeDto
import dev.fuju.core.network.throwIfError
import dev.fuju.core.network.wrapAsAuthException
import dev.fuju.feature.profile.domain.ProfileRepository
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ProfileRepositoryImpl(
    private val client: HttpClient,
) : ProfileRepository {
    override suspend fun getMe(): Me =
        wrap {
            val dto: MeDto = client.get("/me").also { it.throwIfError() }.body()
            dto.toDomain()
        }

    override suspend fun listUsers(
        limit: Int,
        offset: Int,
    ): List<ProfileUser> =
        wrap {
            val list: List<UserDto> =
                client
                    .get("/users") {
                        parameter("limit", limit)
                        parameter("offset", offset)
                    }.also { it.throwIfError() }
                    .body()
            list.map { it.toDomain() }
        }

    override suspend fun getUser(sub: String): ProfileUser =
        wrap {
            val dto: UserDto = client.get("/users/$sub").also { it.throwIfError() }.body()
            dto.toDomain()
        }

    override suspend fun updateUser(
        sub: String,
        input: UpdateProfileInput,
    ): ProfileUser =
        wrap {
            val dto: UserDto =
                client
                    .put("/users/$sub") {
                        contentType(ContentType.Application.Json)
                        setBody(UpdateProfileDto(bio = input.bio, bannerUrl = input.bannerUrl))
                    }.also { it.throwIfError() }
                    .body()
            dto.toDomain()
        }

    override suspend fun follow(sub: String): FollowResult =
        wrap {
            val dto: FollowResultDto = client.post("/users/$sub/follow").also { it.throwIfError() }.body()
            FollowResult(dto.following, dto.followersCount)
        }

    override suspend fun unfollow(sub: String): FollowResult =
        wrap {
            val dto: FollowResultDto = client.delete("/users/$sub/follow").also { it.throwIfError() }.body()
            FollowResult(dto.following, dto.followersCount)
        }

    override suspend fun followers(
        sub: String,
        limit: Int,
        offset: Int,
    ): List<ProfileUser> =
        wrap {
            val list: List<UserDto> =
                client
                    .get("/users/$sub/followers") {
                        parameter("limit", limit)
                        parameter("offset", offset)
                    }.also { it.throwIfError() }
                    .body()
            list.map { it.toDomain() }
        }

    override suspend fun following(
        sub: String,
        limit: Int,
        offset: Int,
    ): List<ProfileUser> =
        wrap {
            val list: List<UserDto> =
                client
                    .get("/users/$sub/following") {
                        parameter("limit", limit)
                        parameter("offset", offset)
                    }.also { it.throwIfError() }
                    .body()
            list.map { it.toDomain() }
        }

    private inline fun <T> wrap(block: () -> T): T = wrapAsAuthException(block)
}

@Serializable
internal data class UserDto(
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
internal data class MeDto(
    val sub: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("display_id") val displayId: String,
    @SerialName("icon_url") val iconUrl: String,
    val bio: String = "",
    @SerialName("banner_url") val bannerUrl: String = "",
    val badges: List<BadgeDto> = emptyList(),
    @SerialName("created_at") val createdAt: String,
    @SerialName("profile_refreshed_at") val profileRefreshedAt: String,
    @SerialName("is_admin") val isAdmin: Boolean,
) {
    fun toDomain(): Me =
        Me(
            sub = sub,
            displayName = displayName,
            displayId = displayId,
            iconUrl = iconUrl,
            bio = bio,
            bannerUrl = bannerUrl,
            badges = badges.map { it.toDomain() },
            createdAt = createdAt,
            profileRefreshedAt = profileRefreshedAt,
            isAdmin = isAdmin,
        )
}

@Serializable
internal data class UpdateProfileDto(
    val bio: String? = null,
    @SerialName("banner_url") val bannerUrl: String? = null,
)

@Serializable
internal data class FollowResultDto(
    val following: Boolean,
    @SerialName("followers_count") val followersCount: Int,
)
