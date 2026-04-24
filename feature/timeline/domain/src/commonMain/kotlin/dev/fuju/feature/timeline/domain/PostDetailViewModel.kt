package dev.fuju.feature.timeline.domain

import dev.fuju.core.domain.Post
import dev.fuju.core.error.AuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        reload()
    }

    fun reload() {
        _state.value = _state.value.copy(loadingPost = true, postError = null)
        scope.launch {
            try {
                val post = repository.getPost(postId)
                _state.value = _state.value.copy(post = post, loadingPost = false, postError = null)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loadingPost = false, postError = t.toMessage())
            }
        }
        refreshReplies()
    }

    fun refreshReplies() {
        repliesJob?.cancel()
        _state.value = _state.value.copy(replies = _state.value.replies.copy(loading = true, error = null))
        repliesJob =
            scope.launch {
                try {
                    val page = repository.getReplies(postId, TimelineQuery(cursor = null, limit = pageSize))
                    _state.value =
                        _state.value.copy(
                            replies =
                                PagedList(
                                    items = page.items,
                                    nextCursor = page.nextCursor,
                                    loading = false,
                                    loadingMore = false,
                                    error = null,
                                ),
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.value =
                        _state.value.copy(
                            replies = _state.value.replies.copy(loading = false, error = t.toMessage()),
                        )
                }
            }
    }

    fun loadMoreReplies() {
        val replies = _state.value.replies
        if (!replies.canLoadMore) return
        val cursor = replies.nextCursor ?: return
        _state.value = _state.value.copy(replies = replies.copy(loadingMore = true, error = null))
        scope.launch {
            try {
                val page = repository.getReplies(postId, TimelineQuery(cursor = cursor, limit = pageSize))
                val current = _state.value.replies
                _state.value =
                    _state.value.copy(
                        replies =
                            current.copy(
                                items = current.items + page.items,
                                nextCursor = page.nextCursor,
                                loadingMore = false,
                            ),
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value =
                    _state.value.copy(
                        replies = _state.value.replies.copy(loadingMore = false, error = t.toMessage()),
                    )
            }
        }
    }

    /** Like トグル。親 post / 返信どちらも対象。 */
    fun toggleLike(post: Post) {
        val wasLiked = post.likedByViewer
        val prevCount = post.likesCount
        val nextLiked = !wasLiked
        val nextCount = (prevCount + if (nextLiked) 1 else -1).coerceAtLeast(0)
        applyPostUpdate(post.id) { it.copy(likedByViewer = nextLiked, likesCount = nextCount) }
        scope.launch {
            try {
                if (nextLiked) repository.likePost(post.id) else repository.unlikePost(post.id)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                applyPostUpdate(post.id) { it.copy(likedByViewer = wasLiked, likesCount = prevCount) }
                _state.value =
                    _state.value.copy(replies = _state.value.replies.copy(error = t.toMessage()))
            }
        }
    }

    /** 返信を作成。成功時は末尾に append + 親 post の replies_count を +1。 */
    suspend fun createReply(
        content: String,
        imageIds: List<String> = emptyList(),
    ): Post {
        val reply = repository.createPost(content, imageIds, postId)
        val current = _state.value
        _state.value =
            current.copy(
                post = current.post?.copy(repliesCount = current.post.repliesCount + 1),
                replies = current.replies.copy(items = current.replies.items + reply),
            )
        return reply
    }

    private fun applyPostUpdate(
        id: String,
        transform: (Post) -> Post,
    ) {
        val current = _state.value
        val nextPost = current.post?.let { if (it.id == id) transform(it) else it }
        val nextReplies = current.replies.items.map { if (it.id == id) transform(it) else it }
        _state.value = current.copy(post = nextPost, replies = current.replies.copy(items = nextReplies))
    }

    private fun Throwable.toMessage(): String =
        when (this) {
            is AuthException -> message ?: "エラーが発生しました"
            else -> message ?: "エラーが発生しました"
        }
}
