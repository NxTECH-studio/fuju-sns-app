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
 * Timeline 画面の state holder。React 版 `useTimelineController.ts` +
 * `useTimeline.ts` + `usePostActions.ts` の役割を集約した KMP 共通 ViewModel。
 *
 * Android の AAC ViewModel ではなく、CoroutineScope を外部から注入するシンプルな
 * class に留めている。Compose からは `rememberCoroutineScope()` を渡して生成する。
 *
 * ## 状態管理方針
 * - `StateFlow<PagedList<Post>>` を一本だけ expose する
 * - refresh は cursor を捨てて先頭から再取得（旧 items は一時的に残したまま loading=true）
 * - loadMore は nextCursor を使って追記（空 cursor なら no-op）
 * - like / unlike は **optimistic update**: まず items を書き換え、API 失敗で rollback
 * - create は先頭に prepend（投稿直後のフィードバック）
 * - delete は items から除去（楽観的ではなく成功後に適用）
 *
 * ## 並列リクエストの扱い
 * `loadJob` / `loadMoreJob` / `likeJobs[postId]` を cancel して二重起動を防ぐ。
 * state 更新は `MutableStateFlow.update { it.copy(...) }` でアトミックに read-modify-write
 * するため、同時に走っても lost update にはならない。
 */
class TimelineViewModel(
    private val repository: TimelineRepository,
    private val kind: TimelineKind,
    private val scope: CoroutineScope,
    private val pageSize: Int = TimelineQuery.DEFAULT_LIMIT,
) {
    private val _state = MutableStateFlow(PagedList.initial<Post>())
    val state: StateFlow<PagedList<Post>> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    // 同じ post を素早く複数回 tap した時に optimistic snapshot が競合しないよう、
    // post 単位で進行中の like Job を 1 本に絞る。
    private val likeJobs = mutableMapOf<String, Job>()

    init {
        refresh()
    }

    /**
     * 先頭から再取得する。既存の items はロード中も表示し続けて「真っ白」を避ける。
     */
    fun refresh() {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob =
            scope.launch {
                try {
                    val page = fetchPage(cursor = null)
                    _state.update {
                        PagedList(
                            items = page.items,
                            nextCursor = page.nextCursor,
                            loading = false,
                            loadingMore = false,
                            error = null,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update { it.copy(loading = false, error = sanitizeError(t)) }
                }
            }
    }

    /**
     * nextCursor が残っている限り追記ロード。末端 (`nextCursor == null`) や二重起動は no-op。
     */
    fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        val cursor = current.nextCursor ?: return
        _state.update { it.copy(loadingMore = true, error = null) }
        loadMoreJob =
            scope.launch {
                try {
                    val page = fetchPage(cursor = cursor)
                    _state.update {
                        it.copy(
                            items = it.items + page.items,
                            nextCursor = page.nextCursor,
                            loadingMore = false,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update { it.copy(loadingMore = false, error = sanitizeError(t)) }
                }
            }
    }

    /**
     * Like / Unlike のトグル。React 版 `useLikeToggle.ts` と同じく optimistic。
     * 呼び出し前の `likedByViewer` / `likesCount` を控え、失敗時に元に戻す。
     *
     * 同じ post に対して進行中の like 呼び出しがあれば先に cancel する。rapid double tap
     * 時は最後の tap の結果が勝つ。
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
                    _state.update { it.copy(error = sanitizeError(t)) }
                } finally {
                    likeJobs.remove(post.id)
                }
            }
    }

    /**
     * テキスト + 画像 + 親 post id で新規投稿を作成。成功時は先頭に prepend。
     * 戻り値は呼び出し側で navigate / toast などに使う想定。失敗時は例外。
     */
    suspend fun createPost(
        content: String,
        imageIds: List<String> = emptyList(),
        parentPostId: String? = null,
    ): Post {
        val created = repository.createPost(content, imageIds, parentPostId)
        if (parentPostId == null) {
            _state.update { it.copy(items = listOf(created) + it.items) }
        }
        return created
    }

    /** 投稿を削除。成功後 items から除去する。失敗時は例外。 */
    suspend fun deletePost(id: String) {
        repository.deletePost(id)
        _state.update { s -> s.copy(items = s.items.filter { it.id != id }) }
    }

    /** ユーザにエラーを見せた後の手動クリア用。 */
    fun clearError() {
        _state.update { if (it.error != null) it.copy(error = null) else it }
    }

    private suspend fun fetchPage(cursor: String?): PostPage {
        val query = TimelineQuery(cursor = cursor, limit = pageSize)
        return when (val k = kind) {
            TimelineKind.Home -> repository.getHome(query)
            TimelineKind.Global -> repository.getGlobal(query)
            is TimelineKind.User -> repository.getUser(k.sub, query)
        }
    }

    private fun applyPostUpdate(
        id: String,
        transform: (Post) -> Post,
    ) {
        _state.update { s ->
            s.copy(items = s.items.map { if (it.id == id) transform(it) else it })
        }
    }
}
