package dev.fuju.feature.profile.domain

import dev.fuju.core.domain.Me
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * `/me` の最小キャッシュ。frontend `../frontend/src/hooks/useMe.ts` 相当。
 *
 * Shell 全体で 1 つだけ生成し、`isAdmin` 判定や Settings ハブの「ログイン要否」チェックに使う。
 * 単一画面で完結する場合は [ProfileViewModel] を使えばよく、こちらは shell スコープ向け。
 */
class MeViewModel(
    private val repository: ProfileRepository,
    private val scope: CoroutineScope,
) {
    /**
     * shell が観測する一次的な状態。`Status.Loading` 起点で、`Ready` か `Error` に遷移する。
     */
    sealed interface Status {
        data object Loading : Status

        data class Ready(
            val me: Me,
        ) : Status

        data class Error(
            val message: String,
        ) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Loading)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var loadJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        _status.update { Status.Loading }
        loadJob =
            scope.launch {
                try {
                    val me = repository.getMe()
                    _status.update { Status.Ready(me) }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _status.update { Status.Error(sanitizeError(t)) }
                }
            }
    }

    /** Profile 更新後に最新値を反映する用。 */
    fun setMe(me: Me) {
        _status.update { Status.Ready(me) }
    }
}
