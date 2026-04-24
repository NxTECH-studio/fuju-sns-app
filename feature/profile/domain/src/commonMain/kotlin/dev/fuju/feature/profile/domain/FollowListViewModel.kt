package dev.fuju.feature.profile.domain

import dev.fuju.core.domain.ProfileUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Follower / Following 一覧画面の state holder。React 版 `useFollowList.ts` を KMP に移植。
 *
 * `FollowListKind` で followers / following を切り替え、cursor ベースで追加ロードする。
 * state の形は `feature:timeline:domain` の `PagedList<T>` と同じ方針だが、依存を増やさない
 * ため自前の [FollowListState] を持つ。
 */
class FollowListViewModel(
    private val repository: ProfileRepository,
    private val targetSub: String,
    private val kind: FollowListKind,
    private val scope: CoroutineScope,
    private val pageSize: Int = FollowListQuery.DEFAULT_LIMIT,
) {
    private val _state = MutableStateFlow(FollowListState())
    val state: StateFlow<FollowListState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob =
            scope.launch {
                try {
                    val page = fetch(cursor = null)
                    _state.update {
                        FollowListState(
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

    fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        val cursor = current.nextCursor ?: return
        _state.update { it.copy(loadingMore = true, error = null) }
        loadMoreJob =
            scope.launch {
                try {
                    val page = fetch(cursor = cursor)
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

    fun clearError() {
        _state.update { if (it.error != null) it.copy(error = null) else it }
    }

    private suspend fun fetch(cursor: String?): FollowListPage {
        val query = FollowListQuery(cursor = cursor, limit = pageSize)
        return when (kind) {
            FollowListKind.Followers -> repository.followers(targetSub, query)
            FollowListKind.Following -> repository.following(targetSub, query)
        }
    }
}

/** どちらのリストを読むかの切替え。 */
enum class FollowListKind { Followers, Following }

/**
 * Follow 一覧画面の state。`canLoadMore` は cursor があってロード中でない場合のみ true。
 */
data class FollowListState(
    val items: List<ProfileUser> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
) {
    val canLoadMore: Boolean
        get() = nextCursor != null && !loading && !loadingMore

    val isInitialLoading: Boolean
        get() = loading && items.isEmpty()

    val isEmpty: Boolean
        get() = !loading && error == null && items.isEmpty()
}
