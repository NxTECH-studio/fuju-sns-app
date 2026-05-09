package dev.fuju.feature.admin.domain

import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.ProfileUser
import dev.fuju.feature.profile.domain.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 管理者向け「ユーザーへのバッジ付与/剥奪」画面の state holder。
 * frontend `../frontend/src/routes/admin/AdminUserBadgesRoute.tsx` の振る舞いを写経。
 *
 * - 上段: `/v1/users` を offset paging で読み、ユーザー一覧を出す
 * - 中段: 選択したユーザーの badges を `/v1/users/{sub}` から取得
 * - 下段: badge_key と reason で grant、badge id で revoke
 *
 * grant / revoke 後は targetUser を再取得してバッジ一覧を最新化する。
 */
class AdminUserBadgesViewModel(
    private val adminRepository: AdminRepository,
    private val profileRepository: ProfileRepository,
    private val scope: CoroutineScope,
    private val pageSize: Int = USERS_PAGE_SIZE,
) {
    data class State(
        val users: List<ProfileUser> = emptyList(),
        val limit: Int = USERS_PAGE_SIZE,
        val offset: Int = 0,
        val total: Int = 0,
        val usersLoading: Boolean = true,
        val usersError: String? = null,
        val targetUser: ProfileUser? = null,
        val targetLoading: Boolean = false,
        val targetError: String? = null,
        val grantPending: Boolean = false,
        val grantError: String? = null,
    ) {
        val hasPrev: Boolean get() = offset > 0
        val hasNext: Boolean get() = offset + users.size < total
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var listJob: Job? = null
    private var targetJob: Job? = null
    private var grantJob: Job? = null

    init {
        loadUsers(offset = 0)
    }

    fun loadUsers(offset: Int) {
        listJob?.cancel()
        _state.update { it.copy(offset = offset, usersLoading = true, usersError = null) }
        listJob =
            scope.launch {
                try {
                    val page = profileRepository.listUsers(limit = pageSize, offset = offset)
                    _state.update {
                        it.copy(
                            users = page.items,
                            limit = page.limit,
                            offset = page.offset,
                            total = page.total,
                            usersLoading = false,
                            usersError = null,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update { it.copy(usersLoading = false, usersError = sanitizeAdminError(t)) }
                }
            }
    }

    fun nextPage() {
        if (!_state.value.hasNext) return
        loadUsers(offset = _state.value.offset + pageSize)
    }

    fun prevPage() {
        if (!_state.value.hasPrev) return
        loadUsers(offset = (_state.value.offset - pageSize).coerceAtLeast(0))
    }

    /** ユーザー一覧から / sub 直指定で対象を切り替える。 */
    fun selectTarget(sub: String) {
        targetJob?.cancel()
        _state.update { it.copy(targetLoading = true, targetError = null, targetUser = null) }
        targetJob =
            scope.launch {
                try {
                    val user = profileRepository.getUser(sub)
                    _state.update { it.copy(targetUser = user, targetLoading = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update {
                        it.copy(targetLoading = false, targetError = sanitizeAdminError(t))
                    }
                }
            }
    }

    fun clearTarget() {
        targetJob?.cancel()
        _state.update { it.copy(targetUser = null, targetLoading = false, targetError = null) }
    }

    /** badge_key で付与。成功時に targetUser を再取得して badges を更新する。 */
    suspend fun grant(input: GrantBadgeInput): Badge {
        val target = _state.value.targetUser ?: error("target user is not selected")
        _state.update { it.copy(grantPending = true, grantError = null) }
        try {
            val badge = adminRepository.grantBadge(target.sub, input)
            // バッジ一覧を最新化（race を避けるため単純な再取得）。
            refreshTarget()
            _state.update { it.copy(grantPending = false) }
            return badge
        } catch (t: Throwable) {
            _state.update { it.copy(grantPending = false, grantError = sanitizeAdminError(t)) }
            throw t
        }
    }

    suspend fun revoke(badgeId: String) {
        val target = _state.value.targetUser ?: error("target user is not selected")
        try {
            adminRepository.revokeBadge(target.sub, badgeId)
            refreshTarget()
        } catch (t: Throwable) {
            _state.update { it.copy(grantError = sanitizeAdminError(t)) }
            throw t
        }
    }

    private fun refreshTarget() {
        val sub = _state.value.targetUser?.sub ?: return
        targetJob?.cancel()
        targetJob =
            scope.launch {
                try {
                    val updated = profileRepository.getUser(sub)
                    _state.update { it.copy(targetUser = updated) }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update { it.copy(targetError = sanitizeAdminError(t)) }
                }
            }
    }

    fun clearGrantError() {
        _state.update { if (it.grantError != null) it.copy(grantError = null) else it }
    }

    fun clearTargetError() {
        _state.update { if (it.targetError != null) it.copy(targetError = null) else it }
    }

    fun clearUsersError() {
        _state.update { if (it.usersError != null) it.copy(usersError = null) else it }
    }

    companion object {
        const val USERS_PAGE_SIZE: Int = 20
    }
}
