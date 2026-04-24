package dev.fuju.feature.timeline.ui

import dev.fuju.core.domain.Author
import dev.fuju.core.domain.Post
import dev.fuju.feature.timeline.domain.PagedList

/**
 * Screenshot test 専用のダミーデータビルダー。
 * prod bundle に漏らさないため、timeline:ui の `androidUnitTest` に閉じて置く。
 */
internal object TestFactories {
    fun fakeAuthor(
        sub: String = "user-001",
        displayName: String = "ふじ花子",
        displayId: String = "hanako",
    ): Author =
        Author(
            sub = sub,
            displayName = displayName,
            displayId = displayId,
            iconUrl = "",
        )

    fun fakePost(
        id: String = "post-001",
        content: String = "はじめての投稿です。ComposeMultiplatform 楽しい。",
        likesCount: Int = 3,
        repliesCount: Int = 1,
        likedByViewer: Boolean = false,
        author: Author? = fakeAuthor(),
        createdAt: String = "2026-04-24T10:15:30Z",
    ): Post =
        Post(
            id = id,
            userId = author?.sub ?: "unknown",
            content = content,
            parentPostId = null,
            rootPostId = id,
            likesCount = likesCount,
            repliesCount = repliesCount,
            visibility = "public",
            createdAt = createdAt,
            updatedAt = createdAt,
            images = emptyList(),
            tags = emptyList(),
            author = author,
            ogpPreviews = emptyList(),
            likedByViewer = likedByViewer,
            followingAuthor = false,
        )

    /** 3 件の投稿が並ぶ通常状態の PagedList。 */
    fun populatedTimeline(): PagedList<Post> =
        PagedList(
            items =
                listOf(
                    fakePost(
                        id = "post-001",
                        content = "はじめての投稿です。ComposeMultiplatform 楽しい。",
                        likesCount = 5,
                        repliesCount = 2,
                    ),
                    fakePost(
                        id = "post-002",
                        content = "screenshot test の baseline 整備中。Roborazzi で pixel diff が検出できる。",
                        author = fakeAuthor(sub = "user-002", displayName = "藤田たろう", displayId = "taro"),
                        likesCount = 12,
                        likedByViewer = true,
                        repliesCount = 3,
                        createdAt = "2026-04-24T09:45:00Z",
                    ),
                    fakePost(
                        id = "post-003",
                        content = "KMP + Compose はいいぞ。",
                        author = fakeAuthor(sub = "user-003", displayName = "Moe", displayId = "moe"),
                        likesCount = 0,
                        repliesCount = 0,
                        createdAt = "2026-04-23T22:00:00Z",
                    ),
                ),
            nextCursor = null,
            loading = false,
        )

    /** 初回ロード完了かつ空 → EmptyState 分岐。 */
    fun emptyTimeline(): PagedList<Post> =
        PagedList(
            items = emptyList(),
            nextCursor = null,
            loading = false,
            error = null,
        )

    /** エラー状態（初回ロード失敗）。 */
    fun errorTimeline(): PagedList<Post> =
        PagedList(
            items = emptyList(),
            nextCursor = null,
            loading = false,
            error = "ネットワークに接続できませんでした",
        )
}
