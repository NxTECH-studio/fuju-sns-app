package dev.fuju.core.domain

import kotlinx.serialization.Serializable

/**
 * Backend の badges。`../frontend/src/types/vm.ts` の `BadgeVM` を移植。
 */
@Serializable
data class Badge(
    val id: String,
    val key: String,
    val label: String,
    val description: String,
    val iconUrl: String,
    val color: String,
    val priority: Int,
)

@Serializable
data class Author(
    val sub: String,
    val displayName: String,
    val displayId: String,
    val iconUrl: String,
)

@Serializable
data class ProfileUser(
    val sub: String,
    val displayName: String,
    val displayId: String,
    val iconUrl: String,
    val bio: String,
    val bannerUrl: String,
    val badges: List<Badge>,
    val createdAt: String,
    val profileRefreshedAt: String,
)

@Serializable
data class Me(
    val sub: String,
    val displayName: String,
    val displayId: String,
    val iconUrl: String,
    val bio: String,
    val bannerUrl: String,
    val badges: List<Badge>,
    val createdAt: String,
    val profileRefreshedAt: String,
    val isAdmin: Boolean,
)

@Serializable
data class PostImage(
    val id: String,
    val publicUrl: String,
    val position: Int,
)

@Serializable
data class PostTag(
    val id: String,
    val name: String,
)

@Serializable
data class OGPPreview(
    val url: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val siteName: String,
    val canonicalUrl: String,
)

/**
 * SNS 投稿の VM。`../frontend/src/types/vm.ts` の `PostVM` を移植。
 */
@Serializable
data class Post(
    val id: String,
    val userId: String,
    val content: String,
    val parentPostId: String?,
    val rootPostId: String?,
    val likesCount: Int,
    val repliesCount: Int,
    val visibility: String,
    val createdAt: String,
    val updatedAt: String,
    val images: List<PostImage>,
    val tags: List<PostTag>,
    val author: Author?,
    val ogpPreviews: List<OGPPreview>,
    val likedByViewer: Boolean,
    val followingAuthor: Boolean,
)

@Serializable
data class FollowResult(
    val following: Boolean,
    val followersCount: Int,
)
