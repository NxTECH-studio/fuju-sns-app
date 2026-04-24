package dev.fuju.feature.profile.domain

import dev.fuju.core.domain.Me
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.domain.UpdateProfileInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ユーザープロフィール画面の state holder。React 版 `useUserProfile.ts` + `useFollowToggle.ts`
 * + `useProfileEdit.ts` の役割を 1 つの KMP 共通 ViewModel に集約する。
 *
 * ## 振る舞い
 * - `targetSub == null` の場合は「自分のプロフィール」モード。`/me` で自分の sub を解決し、
 *   その後同じ `sub` で `/users/{sub}` を取って [ProfileState.user] に格納する
 * - `targetSub != null` の場合は「他人のプロフィール」モード。`/users/{sub}` のみ取得し、
 *   `/me` も並行で取得して "自分" 判定（`isSelf`）に使う
 * - follow / unfollow は **optimistic update**。呼び出し前の `following` / `followersCount`
 *   をスナップショットし、失敗時に rollback する
 * - edit は backend が返した最新値で state を上書きする
 *
 * ## 並列リクエストの扱い
 * `loadJob` / `followJob` を cancel して二重起動を防ぐ。state 更新は `MutableStateFlow.update { }`
 * でアトミックに read-modify-write するため、同時に走っても lost update にはならない。
 *
 * backend は `/users/{sub}` のレスポンスに follow state を含めないため、初期値は [syncFollowState]
 * で外部から供給する（React 版はユーザーの timeline の先頭 post から推測している）。
 */
class ProfileViewModel(
    private val repository: ProfileRepository,
    private val targetSub: String?,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var followJob: Job? = null

    init {
        reload()
    }

    /** 自分 / 他人の判定。両方ロード済みのときのみ確定する。 */
    val isSelf: Boolean
        get() {
            val me = _state.value.me ?: return false
            val u = _state.value.user ?: return false
            return me.sub == u.sub
        }

    /**
     * プロフィール本体を再取得する。`targetSub == null` なら `/me` 経由で自分の sub を解決し、
     * そうでなければ `/me` と `/users/{sub}` を並行で取得する。
     */
    fun reload() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob =
            scope.launch {
                try {
                    val me = repository.getMe()
                    val sub = targetSub ?: me.sub
                    val user = repository.getUser(sub)
                    _state.update {
                        it.copy(
                            me = me,
                            user = user,
                            loading = false,
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
     * `/users/{sub}` は follow state を返さないので、timeline 側で得た `following_author` を
     * 後追いで反映する。呼び出しタイミングが未確定なので state はあくまで補助情報扱い。
     */
    fun syncFollowState(
        following: Boolean,
        followersCount: Int? = null,
    ) {
        _state.update { s ->
            s.copy(
                following = following,
                followersCount = followersCount ?: s.followersCount,
                followStateKnown = true,
            )
        }
    }

    /**
     * フォロー / アンフォローのトグル。React 版 `useFollowToggle.ts` と同じく optimistic。
     * 呼び出し前の `following` / `followersCount` を控え、失敗時に元に戻す。
     *
     * 同じユーザーに対して進行中の follow 呼び出しがあれば先に cancel する。rapid double tap
     * では最後の tap の結果が勝つ。
     */
    fun toggleFollow() {
        val current = _state.value
        val targetUser = current.user ?: return
        val wasFollowing = current.following
        val prevCount = current.followersCount ?: 0
        val nextFollowing = !wasFollowing
        val nextCount = (prevCount + if (nextFollowing) 1 else -1).coerceAtLeast(0)
        _state.update {
            it.copy(
                following = nextFollowing,
                followersCount = nextCount,
                followStateKnown = true,
                followPending = true,
                error = null,
            )
        }
        followJob?.cancel()
        followJob =
            scope.launch {
                try {
                    val result =
                        if (nextFollowing) {
                            repository.follow(targetUser.sub)
                        } else {
                            repository.unfollow(targetUser.sub)
                        }
                    _state.update {
                        it.copy(
                            following = result.following,
                            followersCount = result.followersCount,
                            followStateKnown = true,
                            followPending = false,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    _state.update {
                        it.copy(
                            following = wasFollowing,
                            followersCount = prevCount,
                            followPending = false,
                            error = sanitizeError(t),
                        )
                    }
                }
            }
    }

    /**
     * 自分のプロフィール編集。`targetSub` が自分以外 / `me` 未解決の場合は例外。
     * 成功時は `user` / `me` の両方を最新値で上書きする。
     */
    suspend fun updateProfile(input: UpdateProfileInput): Me {
        val me = _state.value.me ?: error("profile not loaded")
        val updated = repository.updateUser(me.sub, input)
        _state.update { s ->
            s.copy(
                me = updated,
                user =
                    s.user?.copy(
                        bio = updated.bio,
                        bannerUrl = updated.bannerUrl,
                    ),
            )
        }
        return updated
    }

    fun clearError() {
        _state.update { if (it.error != null) it.copy(error = null) else it }
    }
}

/**
 * プロフィール画面の state。`user` が null の間は loading / error を見て分岐表示する。
 *
 * @param me 自分のアカウント情報。`reload` で `/me` を取得。`user.sub == me.sub` で自分判定。
 * @param user 表示対象のプロフィール。自分プロフィールの場合は `/me` と同じ sub のものが入る
 * @param following ビューアーがこのユーザーをフォローしているか
 * @param followersCount 表示用のフォロワー数。初期値は不明（swagger に無い）なので null
 * @param followStateKnown `following` / `followersCount` が有効な値として確定したか
 * @param followPending follow / unfollow の進行中フラグ（ボタンを disable する用途）
 * @param loading プロフィール本体の取得中
 * @param error ユーザー向けに表示するエラーメッセージ
 */
data class ProfileState(
    val me: Me? = null,
    val user: ProfileUser? = null,
    val following: Boolean = false,
    val followersCount: Int? = null,
    val followStateKnown: Boolean = false,
    val followPending: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)
