package dev.fuju.feature.admin.domain

import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.ProfileUser
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
 * Admin 画面の state holder。React 版 `useAdminBadges.ts` + `useUsers.ts` を集約した KMP 共通
 * ViewModel。Badge マスタの CRUD（create / update）、ユーザーへの grant / revoke、ユーザー
 * 一覧の取得とクライアントサイド検索を提供する。
 *
 * Badge の **削除** は backend に endpoint が無いため未提供。
 *
 * ## 並列リクエストの扱い
 * - `loadBadgesJob` / `loadUsersJob` を cancel して二重起動を防ぐ
 * - per-(userSub, badgeKey) の grant Job、per-(userSub, badgeId) の revoke Job を map で管理し、
 *   同じ対象に対する rapid double tap で competing request が走らないようにする
 * - state 更新は `MutableStateFlow.update { it.copy(...) }` でアトミックに read-modify-write
 *
 * ## Optimistic update
 * - `createBadge` / `updateBadge` は server response (確定値) で badges を書き換える
 * - `grant` / `revoke` は呼び出し側 (ユーザー詳細画面) で per-user の badges を扱うため、
 *   ここでは badges 一覧そのものは触らない。grant の戻り値 [Badge] は呼び出し側が user の
 *   badges に詰める想定
 *
 * ## エラー
 * 例外は [AdminErrorMessages.sanitizeError] で UI 向け文字列に正規化し、`error` field に格納
 * する。raw な `t.message` を UI に出さない。
 */
class AdminViewModel(
    private val repository: AdminRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AdminState())
    val state: StateFlow<AdminState> = _state.asStateFlow()

    private var loadBadgesJob: Job? = null
    private var loadUsersJob: Job? = null

    // (sub, badgeKey) 単位、(sub, badgeId) 単位で進行中の grant / revoke を 1 つに絞る。
    private val grantJobs = mutableMapOf<Pair<String, String>, Job>()
    private val revokeJobs = mutableMapOf<Pair<String, String>, Job>()

    init {
        reloadBadges()
        reloadUsers()
    }

    /** Badge マスタを再取得する。priority 降順で並べてから state に格納する。 */
    fun reloadBadges() {
        loadBadgesJob?.cancel()
        _state.update { it.copy(badgesLoading = true, error = null) }
        loadBadgesJob =
            scope.launch {
                try {
                    val list = repository.listBadges().sortedByDescending { it.priority }
                    _state.update { it.copy(badges = list, badgesLoading = false, error = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update {
                        it.copy(badgesLoading = false, error = AdminErrorMessages.sanitizeError(t))
                    }
                }
            }
    }

    /** ユーザー一覧を再取得する。Admin 画面の検索ボックス用。 */
    fun reloadUsers() {
        loadUsersJob?.cancel()
        _state.update { it.copy(usersLoading = true, error = null) }
        loadUsersJob =
            scope.launch {
                try {
                    val list = repository.listUsers()
                    _state.update { it.copy(users = list, usersLoading = false, error = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update {
                        it.copy(usersLoading = false, error = AdminErrorMessages.sanitizeError(t))
                    }
                }
            }
    }

    /**
     * Badge を新規作成する。成功時は state.badges に append（priority 降順で再ソート）。
     * 失敗時は例外を呼び出し側に伝播し、合わせて state.error に文言を立てる。
     */
    suspend fun createBadge(input: CreateBadgeInput): Badge {
        try {
            val created = repository.createBadge(input)
            _state.update { s ->
                s.copy(
                    badges = (s.badges + created).sortedByDescending { it.priority },
                    error = null,
                )
            }
            return created
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            _state.update { it.copy(error = AdminErrorMessages.sanitizeError(t)) }
            throw t
        }
    }

    /**
     * Badge を更新する。成功時は state.badges 内の同 id を入れ替えて再ソート。失敗時は例外を
     * 伝播し、state.error に文言を立てる。
     */
    suspend fun updateBadge(
        id: String,
        input: UpdateBadgeInput,
    ): Badge {
        try {
            val updated = repository.updateBadge(id, input)
            _state.update { s ->
                s.copy(
                    badges =
                        s.badges
                            .map { if (it.id == id) updated else it }
                            .sortedByDescending { it.priority },
                    error = null,
                )
            }
            return updated
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            _state.update { it.copy(error = AdminErrorMessages.sanitizeError(t)) }
            throw t
        }
    }

    /**
     * 指定ユーザーにバッジを付与する。**Optimistic** で UI 側の user.badges を増やせるよう、
     * server response の [Badge] を返す（呼び出し側で詰め替え）。失敗時は例外を伝播。
     *
     * 同じ (userSub, badgeKey) に対して進行中の grant があれば cancel する。
     */
    fun grantBadge(
        userSub: String,
        input: GrantBadgeInput,
        onResult: (Result<Badge>) -> Unit,
    ) {
        val key = userSub to input.badgeKey
        grantJobs[key]?.cancel()
        grantJobs[key] =
            scope.launch {
                try {
                    val granted = repository.grantBadge(userSub, input)
                    _state.update { it.copy(error = null) }
                    onResult(Result.success(granted))
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update { it.copy(error = AdminErrorMessages.sanitizeError(t)) }
                    onResult(Result.failure(t))
                } finally {
                    grantJobs.remove(key)
                }
            }
    }

    /**
     * 指定ユーザーから badge を剥奪する。idempotent。同じ (userSub, badgeId) に対して進行中の
     * revoke があれば cancel する。完了通知は [onResult] で受ける。
     */
    fun revokeBadge(
        userSub: String,
        badgeId: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val key = userSub to badgeId
        revokeJobs[key]?.cancel()
        revokeJobs[key] =
            scope.launch {
                try {
                    repository.revokeBadge(userSub, badgeId)
                    _state.update { it.copy(error = null) }
                    onResult(Result.success(Unit))
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update { it.copy(error = AdminErrorMessages.sanitizeError(t)) }
                    onResult(Result.failure(t))
                } finally {
                    revokeJobs.remove(key)
                }
            }
    }

    /** ユーザにエラーを見せた後の手動クリア用。 */
    fun clearError() {
        _state.update { if (it.error != null) it.copy(error = null) else it }
    }
}

/**
 * Admin 画面の state。
 *
 * @param badges Badge マスタ。priority 降順で並ぶ
 * @param users 検索対象のユーザー全件。検索ボックスでクライアントサイドフィルタする
 * @param badgesLoading Badge マスタの取得中
 * @param usersLoading ユーザー一覧の取得中
 * @param error UI 向けに正規化済みのエラー文言。raw な例外は格納しない
 */
data class AdminState(
    val badges: List<Badge> = emptyList(),
    val users: List<ProfileUser> = emptyList(),
    val badgesLoading: Boolean = true,
    val usersLoading: Boolean = true,
    val error: String? = null,
)

/**
 * 検索クエリでユーザーを絞る純粋関数。`displayName` / `displayId` / `sub` の
 * 大文字小文字を無視した部分一致でマッチさせる。
 *
 * クエリが空白のみなら全件をそのまま返す。
 */
fun filterUsers(
    users: List<ProfileUser>,
    query: String,
): List<ProfileUser> {
    val needle = query.trim()
    if (needle.isEmpty()) return users
    val lower = needle.lowercase()
    return users.filter { u ->
        u.displayName.lowercase().contains(lower) ||
            u.displayId.lowercase().contains(lower) ||
            u.sub.lowercase().contains(lower)
    }
}
