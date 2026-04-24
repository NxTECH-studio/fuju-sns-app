package dev.fuju.feature.timeline.data

import dev.fuju.core.domain.Author
import dev.fuju.core.domain.OGPPreview
import dev.fuju.core.domain.Post
import dev.fuju.core.domain.PostImage
import dev.fuju.core.domain.PostTag
import dev.fuju.core.error.toAuthException
import dev.fuju.core.network.throwIfError
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
 * Backend `/timeline/*`, `/posts/*` を叩く実装。
 * React 版 `../frontend/src/api/endpoints/timelines.ts` / `posts.ts` を移植。
 */
class TimelineRepositoryImpl(private val client: HttpClient) : TimelineRepository {

    override suspend fun getHome(query: TimelineQuery): List<Post> = fetchTimeline("/timeline/home", query)
    override suspend fun getGlobal(query: TimelineQuery): List<Post> = fetchTimeline("/timeline/global", query)
    override suspend fun getUser(sub: String, query: TimelineQuery): List<Post> =
        fetchTimeline("/timeline/user/$sub", query)

    override suspend fun getPost(id: String): Post = wrap {
        val dto: PostDto = client.get("/posts/$id").also { it.throwIfError() }.body()
        dto.toDomain()
    }

    override suspend fun getReplies(id: String, query: TimelineQuery): List<Post> = wrap {
        val list: List<PostDto> = client.get("/posts/$id/replies") {
            parameter("limit", query.limit)
            parameter("offset", query.offset)
        }.also { it.throwIfError() }.body()
        list.map { it.toDomain() }
    }

    override suspend fun likePost(id: String) {
        wrap { client.post("/posts/$id/like").throwIfError() }
    }

    override suspend fun unlikePost(id: String) {
        wrap { client.delete("/posts/$id/like").throwIfError() }
    }

    override suspend fun createPost(content: String, imageIds: List<String>, parentPostId: String?): Post = wrap {
        val res: PostDto = client.post("/posts") {
            contentType(ContentType.Application.Json)
            setBody(CreatePostDto(content = content, imageIds = imageIds, parentPostId = parentPostId))
        }.also { it.throwIfError() }.body()
        res.toDomain()
    }

    override suspend fun deletePost(id: String) {
        wrap { client.delete("/posts/$id").throwIfError() }
    }

    private suspend fun fetchTimeline(path: String, query: TimelineQuery): List<Post> = wrap {
        val list: List<PostDto> = client.get(path) {
            parameter("limit", query.limit)
            parameter("offset", query.offset)
        }.also { it.throwIfError() }.body()
        list.map { it.toDomain() }
    }

    private inline fun <T> wrap(block: () -> T): T =
        try {
            block()
        } catch (t: Throwable) {
            throw t.toAuthException()
        }
}

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
    val author: AuthorDto? = null,
    @SerialName("ogp_previews") val ogpPreviews: List<OGPDto> = emptyList(),
    @SerialName("liked_by_viewer") val likedByViewer: Boolean = false,
    @SerialName("following_author") val followingAuthor: Boolean = false,
) {
    fun toDomain(): Post = Post(
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
        author = author?.let { Author(it.sub, it.displayName, it.displayId, it.iconUrl) },
        ogpPreviews = ogpPreviews.map {
            OGPPreview(it.url, it.title, it.description, it.imageUrl, it.siteName, it.canonicalUrl)
        },
        likedByViewer = likedByViewer,
        followingAuthor = followingAuthor,
    )
}

@Serializable
internal data class PostImageDto(val id: String, @SerialName("public_url") val publicUrl: String, val position: Int)

@Serializable
internal data class PostTagDto(val id: String, val name: String)

@Serializable
internal data class AuthorDto(
    val sub: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("display_id") val displayId: String,
    @SerialName("icon_url") val iconUrl: String,
)

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
