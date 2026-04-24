package dev.fuju.feature.timeline.domain

import dev.fuju.core.domain.Post
import kotlinx.coroutines.CompletableDeferred

/**
 * テスト用の `TimelineRepository` in-memory 実装。
 * 呼び出しの記録 + 次の応答を sequential に差し替え可能にする最小の fake。
 */
class FakeTimelineRepository : TimelineRepository {
    val homeCalls = mutableListOf<TimelineQuery>()
    val globalCalls = mutableListOf<TimelineQuery>()
    val userCalls = mutableListOf<Pair<String, TimelineQuery>>()
    val likeCalls = mutableListOf<String>()
    val unlikeCalls = mutableListOf<String>()
    val createCalls = mutableListOf<CreatePostInput>()
    val deleteCalls = mutableListOf<String>()

    /** 取得系の応答キュー。空になったら例外を投げる。 */
    private val homeResponses = ArrayDeque<Result<PostPage>>()
    private val globalResponses = ArrayDeque<Result<PostPage>>()
    private val userResponses = ArrayDeque<Result<PostPage>>()
    private val getPostResponses = ArrayDeque<Result<Post>>()
    private val repliesResponses = ArrayDeque<Result<PostPage>>()

    /** like / unlike / create / delete の次の応答。null なら成功。 */
    var nextLikeError: Throwable? = null
    var nextUnlikeError: Throwable? = null
    var nextCreateError: Throwable? = null
    var nextDeleteError: Throwable? = null

    /** createPost の戻り値を組み立てるラムダ。 */
    var createResponse: (CreatePostInput) -> Post = { input ->
        samplePost(
            id = "new-${createCalls.size}",
            content = input.content,
            parentPostId = input.parentPostId,
        )
    }

    /** 挙動を遅延させたい場合に待機を挟む Deferred。テストで `complete()` を呼ぶ。 */
    var likeGate: CompletableDeferred<Unit>? = null

    fun enqueueHome(page: PostPage) {
        homeResponses += Result.success(page)
    }

    fun enqueueHomeError(error: Throwable) {
        homeResponses += Result.failure(error)
    }

    fun enqueueGlobal(page: PostPage) {
        globalResponses += Result.success(page)
    }

    fun enqueueUser(page: PostPage) {
        userResponses += Result.success(page)
    }

    fun enqueueGetPost(post: Post) {
        getPostResponses += Result.success(post)
    }

    fun enqueueGetPostError(error: Throwable) {
        getPostResponses += Result.failure(error)
    }

    fun enqueueReplies(page: PostPage) {
        repliesResponses += Result.success(page)
    }

    override suspend fun getHome(query: TimelineQuery): PostPage {
        homeCalls += query
        return homeResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected getHome call: ${homeCalls.size}")
    }

    override suspend fun getGlobal(query: TimelineQuery): PostPage {
        globalCalls += query
        return globalResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected getGlobal call")
    }

    override suspend fun getUser(
        sub: String,
        query: TimelineQuery,
    ): PostPage {
        userCalls += sub to query
        return userResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected getUser call")
    }

    override suspend fun getPost(id: String): Post =
        getPostResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected getPost($id) call")

    override suspend fun getReplies(
        id: String,
        query: TimelineQuery,
    ): PostPage =
        repliesResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected getReplies($id) call")

    override suspend fun likePost(id: String) {
        likeGate?.await()
        likeCalls += id
        nextLikeError?.let { throw it }
    }

    override suspend fun unlikePost(id: String) {
        likeGate?.await()
        unlikeCalls += id
        nextUnlikeError?.let { throw it }
    }

    override suspend fun createPost(
        content: String,
        imageIds: List<String>,
        parentPostId: String?,
    ): Post {
        val input = CreatePostInput(content, imageIds, parentPostId)
        createCalls += input
        nextCreateError?.let { throw it }
        return createResponse(input)
    }

    override suspend fun deletePost(id: String) {
        deleteCalls += id
        nextDeleteError?.let { throw it }
    }
}

data class CreatePostInput(
    val content: String,
    val imageIds: List<String>,
    val parentPostId: String?,
)

internal fun samplePost(
    id: String,
    content: String = "hello from $id",
    likedByViewer: Boolean = false,
    likesCount: Int = 0,
    repliesCount: Int = 0,
    parentPostId: String? = null,
): Post =
    Post(
        id = id,
        userId = "user-$id",
        content = content,
        parentPostId = parentPostId,
        rootPostId = parentPostId ?: id,
        likesCount = likesCount,
        repliesCount = repliesCount,
        visibility = "public",
        createdAt = "2026-04-24T10:00:00Z",
        updatedAt = "2026-04-24T10:00:00Z",
        images = emptyList(),
        tags = emptyList(),
        author = null,
        ogpPreviews = emptyList(),
        likedByViewer = likedByViewer,
        followingAuthor = false,
    )
