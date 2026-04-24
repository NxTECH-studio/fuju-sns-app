package dev.fuju.feature.profile.data

import dev.fuju.core.domain.FollowResult
import dev.fuju.core.domain.Me
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.domain.UpdateProfileInput
import dev.fuju.core.network.dto.BadgeDto
import dev.fuju.core.network.throwIfError
import dev.fuju.core.network.wrapAsAuthException
import dev.fuju.feature.profile.domain.FollowListPage
import dev.fuju.feature.profile.domain.FollowListQuery
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
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Backend `/me` / `/users` / `/users/{sub}` / `/users/{sub}/{follow,followers,following}` を
 * 叩く Repository 実装。React 版 `../frontend/src/api/endpoints/{me,users,follows}.ts` を移植。
 *
 * ## Backend スキーマの注意点（`backend/docs/swagger.yaml` 参照）
 * - `/me` / `/users/{sub}` / `PUT /users/{sub}` は **エンベロープ付き**（`{ data: ... }`）
 * - `/users` (list) は offset paging の `UserListResponse { data, limit, offset, total }`
 * - `/users/{sub}/follow` (POST/DELETE) は `FollowResultEnvelope { data: { following, followers_count } }`
 * - `/users/{sub}/{followers,following}` は cursor paging の `FollowListResponse { data, next_cursor }`
 *
 * React 版は mapper (`services/mappers.ts`) で camelCase へ正規化しているが、ここでは
 * DTO 側で `@SerialName` を付けて直接受け、`toDomain()` で domain 型に詰め替える。
 *
 * ## URL encoding
 * `sub` はバックエンド上は ULID だが、念のため `encodeURLPathPart()` でエスケープして
 * path injection を防ぐ。`TimelineRepositoryImpl` と同じ方針。
 */
class ProfileRepositoryImpl(
    private val client: HttpClient,
) : ProfileRepository {
    override suspend fun getMe(): Me =
        wrap {
            val res: SelfUserEnvelopeDto = client.get("/me").also { it.throwIfError() }.body()
            res.data.toDomain()
        }

    override suspend fun listUsers(
        limit: Int,
        offset: Int,
    ): List<ProfileUser> =
        wrap {
            val res: UserListResponseDto =
                client
                    .get("/users") {
                        parameter("limit", limit)
                        parameter("offset", offset)
                    }.also { it.throwIfError() }
                    .body()
            res.data.map { it.toDomain() }
        }

    override suspend fun getUser(sub: String): ProfileUser =
        wrap {
            val res: PublicUserEnvelopeDto =
                client
                    .get("/users/${sub.encodeURLPathPart()}")
                    .also { it.throwIfError() }
                    .body()
            res.data.toDomain()
        }

    override suspend fun updateUser(
        sub: String,
        input: UpdateProfileInput,
    ): Me =
        wrap {
            val res: SelfUserEnvelopeDto =
                client
                    .put("/users/${sub.encodeURLPathPart()}") {
                        contentType(ContentType.Application.Json)
                        setBody(UpdateProfileDto(bio = input.bio, bannerUrl = input.bannerUrl))
                    }.also { it.throwIfError() }
                    .body()
            res.data.toDomain()
        }

    override suspend fun follow(sub: String): FollowResult =
        wrap {
            val res: FollowResultEnvelopeDto =
                client
                    .post("/users/${sub.encodeURLPathPart()}/follow")
                    .also { it.throwIfError() }
                    .body()
            FollowResult(following = res.data.following, followersCount = res.data.followersCount)
        }

    override suspend fun unfollow(sub: String): FollowResult =
        wrap {
            val res: FollowResultEnvelopeDto =
                client
                    .delete("/users/${sub.encodeURLPathPart()}/follow")
                    .also { it.throwIfError() }
                    .body()
            FollowResult(following = res.data.following, followersCount = res.data.followersCount)
        }

    override suspend fun followers(
        sub: String,
        query: FollowListQuery,
    ): FollowListPage = fetchFollowList("/users/${sub.encodeURLPathPart()}/followers", query)

    override suspend fun following(
        sub: String,
        query: FollowListQuery,
    ): FollowListPage = fetchFollowList("/users/${sub.encodeURLPathPart()}/following", query)

    private suspend fun fetchFollowList(
        path: String,
        query: FollowListQuery,
    ): FollowListPage =
        wrap {
            val res: FollowListResponseDto =
                client
                    .get(path) {
                        parameter("limit", query.limit)
                        if (query.cursor != null) parameter("cursor", query.cursor)
                    }.also { it.throwIfError() }
                    .body()
            FollowListPage(items = res.data.map { it.toDomain() }, nextCursor = res.nextCursor)
        }

    private inline fun <T> wrap(block: () -> T): T = wrapAsAuthException(block)
}

// ---- DTOs (backend swagger -> KMP) ----

@Serializable
internal data class PublicUserDto(
    val sub: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("display_id") val displayId: String,
    @SerialName("icon_url") val iconUrl: String,
    val bio: String = "",
    @SerialName("banner_url") val bannerUrl: String = "",
    val badges: List<BadgeDto> = emptyList(),
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("deleted_at") val deletedAt: String? = null,
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
internal data class SelfUserDto(
    val sub: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("display_id") val displayId: String,
    @SerialName("icon_url") val iconUrl: String,
    val bio: String = "",
    @SerialName("banner_url") val bannerUrl: String = "",
    val badges: List<BadgeDto> = emptyList(),
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("profile_refreshed_at") val profileRefreshedAt: String,
    @SerialName("is_admin") val isAdmin: Boolean = false,
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
internal data class PublicUserEnvelopeDto(
    val data: PublicUserDto,
)

@Serializable
internal data class SelfUserEnvelopeDto(
    val data: SelfUserDto,
)

@Serializable
internal data class UserListResponseDto(
    val data: List<PublicUserDto>,
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
)

@Serializable
internal data class FollowListResponseDto(
    val data: List<PublicUserDto>,
    @SerialName("next_cursor") val nextCursor: String? = null,
)

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

@Serializable
internal data class FollowResultEnvelopeDto(
    val data: FollowResultDto,
)
