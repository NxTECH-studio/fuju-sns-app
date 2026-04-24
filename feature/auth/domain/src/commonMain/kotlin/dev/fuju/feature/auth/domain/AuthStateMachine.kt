package dev.fuju.feature.auth.domain

import dev.fuju.core.domain.AuthSnapshot
import dev.fuju.core.domain.AuthStatus
import dev.fuju.core.domain.LoginResult
import dev.fuju.core.domain.MFASetupResult
import dev.fuju.core.domain.SocialProvider
import dev.fuju.core.domain.User
import dev.fuju.core.error.AuthException
import dev.fuju.core.error.ErrorCode
import dev.fuju.core.error.toAuthException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Auth state machine。`../auth-component/src/store/AuthStore.ts` の責務を移植。
 *
 * データ層とのやり取りは [AuthActions] に切り出す。state machine 本体は
 * network と UI の橋渡しに徹する。
 */
class AuthStateMachine(
    private val actions: AuthActions,
    private val config: AuthConfig = AuthConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(AuthSnapshot.Initial)
    val state: StateFlow<AuthSnapshot> = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private val loginMutex = Mutex()
    private var refreshJob: Job? = null
    private var accessExpEpochSec: Long? = null
    private var preToken: String? = null
    private var disposed = false

    fun dispose() {
        if (disposed) return
        disposed = true
        refreshJob?.cancel()
        scope.coroutineContext[Job]?.cancelChildren()
    }

    // --- Bootstrap ---
    suspend fun bootstrap() {
        if (_state.value.status != AuthStatus.Idle) return
        update(status = AuthStatus.Authenticating, error = null)
        val hintPresent = actions.readSessionHint() != null
        var hintRetries = 0

        var attempt = 0
        while (!disposed) {
            try {
                val res = actions.refresh()
                accessExpEpochSec = nowEpochSec() + res.expiresInSec
                actions.storeAccessToken(res.accessToken, accessExpEpochSec!!)
                val user = actions.loadProfile()
                val needsSetup = user.linkedProviders.isNotEmpty() && actions.isNewSocialUser(user.id)
                update(status = AuthStatus.Authenticated, user = user, needsPublicIdSetup = needsSetup)
                actions.writeSessionHint(user.id)
                scheduleSilentRefresh()
                return
            } catch (e: AuthException) {
                val status = e.status
                if (status in 400..499 && status != 429) {
                    if (hintPresent && hintRetries < BOOTSTRAP_HINT_RETRY_DELAYS_MS.size) {
                        delay(BOOTSTRAP_HINT_RETRY_DELAYS_MS[hintRetries])
                        hintRetries++
                        continue
                    }
                    clearAll()
                    actions.clearSessionHint()
                    update(status = AuthStatus.Unauthenticated, user = null, error = null)
                    return
                }
                if (status == 0 && attempt < BOOTSTRAP_NETWORK_RETRY_DELAYS_MS.size) {
                    delay(BOOTSTRAP_NETWORK_RETRY_DELAYS_MS[attempt])
                    attempt++
                    continue
                }
                update(status = AuthStatus.Error, error = e)
                return
            } catch (t: Throwable) {
                update(status = AuthStatus.Error, error = t.toAuthException())
                return
            }
        }
    }

    // --- Login ---
    // loginMutex で login / verifyMFA を直列化し、preToken と accessExpEpochSec の
    // 書き換えが同時実行で取り違えられないようにする。
    suspend fun login(
        identifier: String,
        password: String,
    ): LoginResult =
        loginMutex.withLock {
            update(status = AuthStatus.Authenticating, error = null)
            try {
                val res = actions.login(identifier, password)
                if (res.mfaRequired) {
                    preToken = res.preToken
                    update(
                        status = AuthStatus.MFARequired,
                        preTokenPresent = true,
                        mfaAttempts = 0,
                        error = null,
                    )
                    return@withLock LoginResult.MFARequired
                }
                accessExpEpochSec = nowEpochSec() + res.expiresInSec
                actions.storeAccessToken(res.accessToken!!, accessExpEpochSec!!)
                val user = actions.loadProfile()
                update(status = AuthStatus.Authenticated, user = user, error = null)
                actions.writeSessionHint(user.id)
                scheduleSilentRefresh()
                LoginResult.Authenticated(user)
            } catch (e: AuthException) {
                update(
                    status =
                        if (_state.value.user != null) {
                            AuthStatus.Authenticated
                        } else {
                            AuthStatus.Unauthenticated
                        },
                    error = e,
                )
                throw e
            }
        }

    suspend fun verifyMFA(
        code: String? = null,
        recoveryCode: String? = null,
    ) {
        loginMutex.withLock {
            val pt =
                preToken ?: throw AuthException(
                    code = ErrorCode.TOKEN_INVALID,
                    status = 401,
                    message = "no pre-token in scope",
                )
            try {
                val res = actions.verifyMFA(pt, code, recoveryCode)
                preToken = null
                accessExpEpochSec = nowEpochSec() + res.expiresInSec
                actions.storeAccessToken(res.accessToken, accessExpEpochSec!!)
                val user = actions.loadProfile()
                update(
                    status = AuthStatus.Authenticated,
                    user = user,
                    preTokenPresent = false,
                    mfaAttempts = 0,
                    error = null,
                )
                actions.writeSessionHint(user.id)
                scheduleSilentRefresh()
            } catch (e: AuthException) {
                if (e.code == ErrorCode.TOTP_CODE_INVALID || e.code == ErrorCode.RECOVERY_CODE_INVALID) {
                    update(mfaAttempts = _state.value.mfaAttempts + 1)
                }
                throw e
            }
        }
    }

    fun cancelMFA() {
        preToken = null
        actions.clearSessionHint()
        update(
            status = AuthStatus.Unauthenticated,
            user = null,
            preTokenPresent = false,
            mfaAttempts = 0,
            error = null,
        )
    }

    // --- Register ---
    suspend fun register(
        email: String,
        password: String,
        publicId: String,
    ): User = actions.register(email, password, publicId)

    // --- Logout ---
    suspend fun logout() {
        runCatching { actions.logout() }
        clearAll()
        actions.clearSessionHint()
        update(
            status = AuthStatus.Unauthenticated,
            user = null,
            preTokenPresent = false,
            mfaAttempts = 0,
            error = null,
            needsPublicIdSetup = false,
        )
    }

    // --- Refresh ---
    suspend fun refresh(cause: RefreshCause = RefreshCause.MANUAL): Boolean {
        refreshMutex.withLock {
            if (disposed) return false
            return try {
                val res = actions.refresh()
                accessExpEpochSec = nowEpochSec() + res.expiresInSec
                actions.storeAccessToken(res.accessToken, accessExpEpochSec!!)
                if (_state.value.status != AuthStatus.Authenticated) {
                    try {
                        val user = actions.loadProfile()
                        update(status = AuthStatus.Authenticated, user = user, error = null)
                    } catch (_: Throwable) {
                        clearAll()
                        actions.clearSessionHint()
                        update(status = AuthStatus.Unauthenticated, user = null)
                        return false
                    }
                }
                _state.value.user
                    ?.id
                    ?.let(actions::writeSessionHint)
                scheduleSilentRefresh()
                true
            } catch (e: AuthException) {
                if (e.status == 401 || e.status == 403) {
                    clearAll()
                    actions.clearSessionHint()
                    update(status = AuthStatus.Unauthenticated, user = null)
                }
                false
            }
        }
    }

    // --- Profile updates ---
    suspend fun updatePublicId(next: String): User {
        val user = actions.updatePublicId(next)
        update(user = user)
        return user
    }

    suspend fun setupMFA(): MFASetupResult = actions.setupMFA()

    suspend fun enableMFA(code: String): User {
        val user = actions.enableMFA(code)
        update(user = user)
        return user
    }

    suspend fun disableMFA(code: String): User {
        val user = actions.disableMFA(code)
        update(user = user)
        return user
    }

    // --- Social ---
    fun buildSocialConnectURL(
        provider: SocialProvider,
        redirectURI: String,
    ): String = actions.buildConnectURL(provider, redirectURI)

    suspend fun completeSocialCallback(
        provider: SocialProvider,
        state: String,
        code: String,
    ): User {
        try {
            val res = actions.socialCallback(provider, state, code)
            accessExpEpochSec = nowEpochSec() + res.expiresInSec
            actions.storeAccessToken(res.accessToken, accessExpEpochSec!!)
            val user = actions.loadProfile()
            update(
                status = AuthStatus.Authenticated,
                user = user,
                error = null,
                needsPublicIdSetup = actions.isNewSocialUser(user.id),
            )
            actions.writeSessionHint(user.id)
            scheduleSilentRefresh()
            return user
        } catch (e: AuthException) {
            // 呼び出し側（MainActivity / iOSApp）は fire-and-forget で error を受け取れない
            // ため、status = Error に遷移させて UI 経由で通知する。
            update(
                status = if (_state.value.user != null) AuthStatus.Authenticated else AuthStatus.Unauthenticated,
                error = e,
            )
            throw e
        }
    }

    // --- Internal helpers ---
    private fun update(
        status: AuthStatus? = null,
        user: User? = _state.value.user,
        preTokenPresent: Boolean? = null,
        error: AuthException? = _state.value.error,
        mfaAttempts: Int? = null,
        needsPublicIdSetup: Boolean? = null,
    ) {
        if (disposed) return
        val prev = _state.value
        _state.value =
            prev.copy(
                status = status ?: prev.status,
                user = user,
                preTokenPresent = preTokenPresent ?: prev.preTokenPresent,
                error = error,
                mfaAttempts = mfaAttempts ?: prev.mfaAttempts,
                needsPublicIdSetup = needsPublicIdSetup ?: prev.needsPublicIdSetup,
            )
    }

    private fun clearAll() {
        accessExpEpochSec = null
        preToken = null
        refreshJob?.cancel()
        refreshJob = null
    }

    private fun scheduleSilentRefresh() {
        if (disposed || config.disableSilentRefresh) return
        val exp = accessExpEpochSec ?: return
        refreshJob?.cancel()
        val delaySec = (exp - nowEpochSec() - SILENT_REFRESH_LEAD_SEC).coerceAtLeast(MIN_SILENT_REFRESH_DELAY_SEC)
        refreshJob =
            scope.launch {
                delay(delaySec * 1000L)
                refresh(RefreshCause.SILENT)
            }
    }

    private fun nowEpochSec(): Long = actions.nowEpochSec()

    companion object {
        private const val SILENT_REFRESH_LEAD_SEC = 30L
        private const val MIN_SILENT_REFRESH_DELAY_SEC = 5L
        private val BOOTSTRAP_HINT_RETRY_DELAYS_MS = longArrayOf(300L, 1_200L)
        private val BOOTSTRAP_NETWORK_RETRY_DELAYS_MS = longArrayOf(500L, 2_000L, 8_000L)
    }
}

enum class RefreshCause { SILENT, ON_UNAUTHORIZED, MANUAL }

data class AuthConfig(
    val disableSilentRefresh: Boolean = false,
)

/**
 * State machine から data 層へ出る副作用の抽象化。テストでは in-memory の fake を、
 * 本番では `:feature:auth:data` の `AuthRepository` が実装を提供する。
 */
interface AuthActions {
    suspend fun login(
        identifier: String,
        password: String,
    ): LoginResponse

    suspend fun register(
        email: String,
        password: String,
        publicId: String,
    ): User

    suspend fun logout()

    suspend fun refresh(): RefreshResponse

    suspend fun verifyMFA(
        preToken: String,
        code: String?,
        recoveryCode: String?,
    ): VerifyResponse

    suspend fun loadProfile(): User

    suspend fun updatePublicId(next: String): User

    suspend fun setupMFA(): MFASetupResult

    suspend fun enableMFA(code: String): User

    suspend fun disableMFA(code: String): User

    fun buildConnectURL(
        provider: SocialProvider,
        redirectURI: String,
    ): String

    suspend fun socialCallback(
        provider: SocialProvider,
        state: String,
        code: String,
    ): VerifyResponse

    suspend fun storeAccessToken(
        token: String,
        expiresAtEpochSec: Long,
    )

    fun readSessionHint(): String?

    fun writeSessionHint(userId: String)

    fun clearSessionHint()

    fun isNewSocialUser(userId: String): Boolean

    fun nowEpochSec(): Long
}

/**
 * AuthCore の login レスポンス。`mfa_required=true` なら `access_token` は null。
 */
data class LoginResponse(
    val mfaRequired: Boolean,
    val preToken: String? = null,
    val accessToken: String? = null,
    val expiresInSec: Long = 0L,
)

data class RefreshResponse(
    val accessToken: String,
    val expiresInSec: Long,
)

data class VerifyResponse(
    val accessToken: String,
    val expiresInSec: Long,
)
