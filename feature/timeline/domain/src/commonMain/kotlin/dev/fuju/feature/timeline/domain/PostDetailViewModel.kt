package dev.fuju.feature.timeline.domain

import dev.fuju.core.domain.Post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 投稿詳細画面の state。親 post + 返信一覧（cursor ページング）を束ねる。
 * React 版 `usePostDetailController.ts` + `usePostDetail.ts` の役割を統合。
 */
class PostDetailViewModel(
    private val repository: TimelineRepository,
    private val postId: String,
    private val scope: CoroutineScope,
    private val pageSize: Int = TimelineQuery.DEFAULT_LIMIT,
) {
    data class State(
        val post: Post? = null,
        val loadingPost: Boolean = true,
        val postError: String? = null,
        val replies: PagedList<Post> = PagedList.initial(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var repliesJob: Job? = null

    // 同じ post (親 or 返信) への rapid like 連打で optimistic snapshot が競合しないよう
    // post 単位で進行中の Job を 1 本に絞る。
    private val likeJobs = mutableMapOf<String, Job>()

    init {
        reload()
    }

    fun reload() {
        _state.update { it.copy(loadingPost = true, postError = null) }
        scope.launch {
            try {
                val post = repository.getPost(postId)
                _state.update { it.copy(post = post, loadingPost = false, postError = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.update { it.copy(loadingPost = false, postError = sanitizeError(t)) }
            }
        }
        refreshReplies()
    }

    fun refreshReplies() {
        repliesJob?.cancel()
        _state.update { it.copy(replies = it.replies.copy(loading = true, error = null)) }
        repliesJob =
            scope.launch {
                try {
                    val page = repository.getReplies(postId, TimelineQuery(cursor = null, limit = pageSize))
                    _state.update {
                        it.copy(
                            replies =
                                PagedList(
                                    items = page.items,
                                    nextCursor = page.nextCursor,
                                    loading = false,
                                    loadingMore = false,
                                    error = null,
                                ),
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update {
                        it.copy(replies = it.replies.copy(loading = false, error = sanitizeError(t)))
                    }
                }
            }
    }

    fun loadMoreReplies() {
        val replies = _state.value.replies
        if (!replies.canLoadMore) return
        val cursor = replies.nextCursor ?: return
        _state.update { it.copy(replies = it.replies.copy(loadingMore = true, error = null)) }
        scope.launch {
            try {
                val page = repository.getReplies(postId, TimelineQuery(cursor = cursor, limit = pageSize))
                _state.update { s ->
                    s.copy(
                        replies =
                            s.replies.copy(
                                items = s.replies.items + page.items,
                                nextCursor = page.nextCursor,
                                loadingMore = false,
                            ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.update {
                    it.copy(replies = it.replies.copy(loadingMore = false, error = sanitizeError(t)))
                }
            }
        }
    }

    /**
     * Like トグル。親 post / 返信どちらも対象。同じ post への進行中 Job があれば cancel。
     */
    fun toggleLike(post: Post) {
        val wasLiked = post.likedByViewer
        val prevCount = post.likesCount
        val nextLiked = !wasLiked
        val nextCount = (prevCount + if (nextLiked) 1 else -1).coerceAtLeast(0)
        applyPostUpdate(post.id) { it.copy(likedByViewer = nextLiked, likesCount = nextCount) }
        likeJobs[post.id]?.cancel()
        likeJobs[post.id] =
            scope.launch {
                try {
                    if (nextLiked) repository.likePost(post.id) else repository.unlikePost(post.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    applyPostUpdate(post.id) { it.copy(likedByViewer = wasLiked, likesCount = prevCount) }
                    _state.update { it.copy(replies = it.replies.copy(error = sanitizeError(t))) }
                } finally {
                    likeJobs.remove(post.id)
                }
            }
    }

    /** 返信を作成。成功時は末尾に append + 親 post の replies_count を +1。 */
    suspend fun createReply(content: String): Post {
        val reply = repository.createPost(content, postId)
        _state.update { s ->
            s.copy(
                post = s.post?.copy(repliesCount = s.post.repliesCount + 1),
                replies = s.replies.copy(items = s.replies.items + reply),
            )
        }
        return reply
    }

    private fun applyPostUpdate(
        id: String,
        transform: (Post) -> Post,
    ) {
        _state.update { s ->
            val nextPost = s.post?.let { if (it.id == id) transform(it) else it }
            val nextReplies = s.replies.items.map { if (it.id == id) transform(it) else it }
            s.copy(post = nextPost, replies = s.replies.copy(items = nextReplies))
        }
    }
}
