package dev.fuju.feature.admin.domain

import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.UpdateBadgeInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 管理者向け「バッジマスター」画面の state holder。
 * frontend `../frontend/src/hooks/useAdminBadges.ts` 相当。
 *
 * - 初期化時に `/v1/admin/badges` を取得
 * - create / update / delete はサーバー応答後にローカル一覧へ反映
 *   （optimistic 更新は frontend と揃えて行わない）
 */
class AdminBadgesViewModel(
    private val repository: AdminRepository,
    private val scope: CoroutineScope,
) {
    data class State(
        val badges: List<Badge> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob =
            scope.launch {
                try {
                    val list = repository.listBadges()
                    _state.update { it.copy(badges = list, loading = false, error = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update { it.copy(loading = false, error = sanitizeAdminError(t)) }
                }
            }
    }

    suspend fun create(input: CreateBadgeInput): Badge {
        val created = repository.createBadge(input)
        _state.update { it.copy(badges = it.badges + created) }
        return created
    }

    suspend fun update(
        id: String,
        input: UpdateBadgeInput,
    ): Badge {
        val updated = repository.updateBadge(id, input)
        _state.update { s ->
            s.copy(badges = s.badges.map { if (it.id == id) updated else it })
        }
        return updated
    }

    suspend fun delete(id: String) {
        repository.deleteBadge(id)
        _state.update { s -> s.copy(badges = s.badges.filter { it.id != id }) }
    }

    fun clearError() {
        _state.update { if (it.error != null) it.copy(error = null) else it }
    }
}
