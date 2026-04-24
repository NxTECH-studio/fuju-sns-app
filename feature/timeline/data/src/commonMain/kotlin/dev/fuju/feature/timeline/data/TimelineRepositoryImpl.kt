package dev.fuju.feature.timeline.data

import dev.fuju.core.domain.Author
import dev.fuju.core.domain.OGPPreview
import dev.fuju.core.domain.Post
import dev.fuju.core.domain.PostImage
import dev.fuju.core.domain.PostTag
import dev.fuju.core.network.throwIfError
import dev.fuju.core.network.throwIfErrorOrDiscard
import dev.fuju.core.network.wrapAsAuthException
import dev.fuju.feature.timeline.domain.PostPage
import dev.fuju.feature.timeline.domain.TimelineQuery
import dev.fuju.feature.timeline.domain.TimelineRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Backend `/timeline/{home,global,user}` と `/posts/{id}` を叩く実装。
 * React 版 `../frontend/src/api/endpoints/timelines.ts` / `posts.ts` を移植。
 *
 * ## Backend が返すスキーマ（`backend/docs/swagger.yaml` 参照）
 * - リスト系（timeline / posts / replies）: `PostListResponse { data: Post[], next_cursor: string | null }`
 * - 単体 post: `PostDetailEnvelope { data: Post }`
 * - `Post.author` は `PostAuthor` で **フィールドが `_cached` 接尾辞**（`display_name_cached` 等）
 *
 * React 版は mapper (`services/mappers.ts`) で `display_name_cached` → `displayName` に
 * 正規化しており、domain 側は既に正規化済み。ここでも DTO で直接 `_cached` を受け取り、
 * `toDomain()` で [Author] に詰め直す。
 */
class TimelineRepositoryImpl(
    private val client: HttpClient,
) : TimelineRepository {
    override suspend fun getHome(query: TimelineQuery): PostPage = fetchTimeline("/timeline/home", query)

    override suspend fun getGlobal(query: TimelineQuery): PostPage = fetchTimeline("/timeline/global", query)

    override suspend fun getUser(
        sub: String,
        query: TimelineQuery,
    ): PostPage = fetchTimeline("/timeline/user/$sub", query)

    override suspend fun getPost(id: String): Post =
        wrap {
            val res: PostDetailEnvelopeDto =
                client.get("/posts/$id").also { it.throwIfError() }.body()
            res.data.toDomain()
        }

    override suspend fun getReplies(
        id: String,
        query: TimelineQuery,
    ): PostPage =
        wrap {
            val res: PostListResponseDto =
                client
                    .get("/posts/$id/replies") {
                        parameter("limit", query.limit)
                        if (query.cursor != null) parameter("cursor", query.cursor)
                    }.also { it.throwIfError() }
                    .body()
            res.toDomain()
        }

    override suspend fun likePost(id: String) {
        wrap { client.post("/posts/$id/like").throwIfErrorOrDiscard() }
    }

    override suspend fun unlikePost(id: String) {
        wrap { client.delete("/posts/$id/like").throwIfErrorOrDiscard() }
    }

    override suspend fun createPost(
        content: String,
        imageIds: List<String>,
        parentPostId: String?,
    ): Post =
        wrap {
            val res: PostDetailEnvelopeDto =
                client
                    .post("/posts") {
                        contentType(ContentType.Application.Json)
                        setBody(CreatePostDto(content = content, imageIds = imageIds, parentPostId = parentPostId))
                    }.also { it.throwIfError() }
                    .body()
            res.data.toDomain()
        }

    override suspend fun deletePost(id: String) {
        wrap { client.delete("/posts/$id").throwIfErrorOrDiscard() }
    }

    private suspend fun fetchTimeline(
        path: String,
        query: TimelineQuery,
    ): PostPage =
        wrap {
            val res: PostListResponseDto =
                client
                    .get(path) {
                        parameter("limit", query.limit)
                        if (query.cursor != null) parameter("cursor", query.cursor)
                    }.also { it.throwIfError() }
                    .body()
            res.toDomain()
        }

    private inline fun <T> wrap(block: () -> T): T = wrapAsAuthException(block)
}

@Serializable
internal data class PostListResponseDto(
    val data: List<PostDto>,
    @SerialName("next_cursor") val nextCursor: String? = null,
) {
    fun toDomain(): PostPage = PostPage(items = data.map { it.toDomain() }, nextCursor = nextCursor)
}

@Serializable
internal data class PostDetailEnvelopeDto(
    val data: PostDto,
)

@Serializable
internal data class PostDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val content: String,
    @SerialName("parent_post_id") val parentPostId: String? = null,
    @SerialName("root_post_id") val rootPostId: String? = null,
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("replies_count") val repliesCount: Int = 0,
    val visibility: String = "public",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val images: List<PostImageDto> = emptyList(),
    val tags: List<PostTagDto> = emptyList(),
    val author: PostAuthorDto? = null,
    @SerialName("ogp_previews") val ogpPreviews: List<OGPDto> = emptyList(),
    @SerialName("liked_by_viewer") val likedByViewer: Boolean = false,
    @SerialName("following_author") val followingAuthor: Boolean = false,
) {
    fun toDomain(): Post =
        Post(
            id = id,
            userId = userId,
            content = content,
            parentPostId = parentPostId,
            rootPostId = rootPostId,
            likesCount = likesCount,
            repliesCount = repliesCount,
            visibility = visibility,
            createdAt = createdAt,
            updatedAt = updatedAt,
            images = images.map { PostImage(it.id, it.publicUrl, it.position) },
            tags = tags.map { PostTag(it.id, it.name) },
            author = author?.toDomain(),
            ogpPreviews =
                ogpPreviews.map {
                    OGPPreview(it.url, it.title, it.description, it.imageUrl, it.siteName, it.canonicalUrl)
                },
            likedByViewer = likedByViewer,
            followingAuthor = followingAuthor,
        )
}

@Serializable
internal data class PostImageDto(
    val id: String,
    @SerialName("public_url") val publicUrl: String,
    val position: Int,
)

@Serializable
internal data class PostTagDto(
    val id: String,
    val name: String,
)

/**
 * Backend の [PostAuthor] schema。フィールドが `_cached` で終わるのは AuthCore 由来の
 * キャッシュ値（TTL 1h）である旨を示すため。domain [Author] に詰め替えるときに接尾辞を落とす。
 */
@Serializable
internal data class PostAuthorDto(
    val sub: String,
    @SerialName("display_name_cached") val displayName: String,
    @SerialName("display_id_cached") val displayId: String,
    @SerialName("icon_url_cached") val iconUrl: String,
) {
    fun toDomain(): Author = Author(sub = sub, displayName = displayName, displayId = displayId, iconUrl = iconUrl)
}

@Serializable
internal data class OGPDto(
    val url: String,
    val title: String = "",
    val description: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("site_name") val siteName: String = "",
    @SerialName("canonical_url") val canonicalUrl: String = "",
)

@Serializable
internal data class CreatePostDto(
    val content: String,
    @SerialName("image_ids") val imageIds: List<String>,
    @SerialName("parent_post_id") val parentPostId: String?,
)
