package dev.fuju.feature.auth.domain

import dev.fuju.core.domain.MFASetupResult
import dev.fuju.core.domain.SocialProvider
import dev.fuju.core.domain.User
import dev.fuju.core.error.AuthException
import dev.fuju.core.error.ErrorCode

/**
 * テスト用の fake。実装はシンプルに、呼ばれた回数と引数を記録する。
 */
class FakeAuthActions(
    private val initialUser: User? = sampleUser(),
    private val loginBehavior: (String, String) -> LoginResponse = { _, _ ->
        LoginResponse(mfaRequired = false, accessToken = "tkn", expiresInSec = 900L)
    },
    private val refreshBehavior: () -> RefreshResponse = { RefreshResponse("tkn-refresh", 900L) },
    private val now: Long = 1_800_000_000L,
) : AuthActions {
    val tokens = mutableListOf<Pair<String, Long>>()
    val sessionHints = mutableListOf<String?>()
    private var hint: String? = null
    private val newSocialUsers = mutableSetOf<String>()
    var nextProfile: User = initialUser ?: sampleUser()
    var loginCalls = 0
    var refreshCalls = 0
    var verifyCalls = 0

    override suspend fun login(
        identifier: String,
        password: String,
    ): LoginResponse {
        loginCalls++
        return loginBehavior(identifier, password)
    }

    override suspend fun register(
        email: String,
        password: String,
        publicId: String,
    ): User = nextProfile

    override suspend fun logout() { /* noop */ }

    override suspend fun refresh(): RefreshResponse {
        refreshCalls++
        return refreshBehavior()
    }

    override suspend fun verifyMFA(
        preToken: String,
        code: String?,
        recoveryCode: String?,
    ): VerifyResponse {
        verifyCalls++
        if (code == "bad") {
            throw AuthException(code = ErrorCode.TOTP_CODE_INVALID, status = 401, message = "invalid")
        }
        return VerifyResponse("tkn-mfa", 900L)
    }

    override suspend fun loadProfile(): User = nextProfile

    override suspend fun updatePublicId(next: String): User {
        nextProfile = nextProfile.copy(publicId = next)
        return nextProfile
    }

    override suspend fun setupMFA(): MFASetupResult = MFASetupResult("secret", "data:png;base64,", listOf("rc1"))

    override suspend fun enableMFA(code: String): User = nextProfile.copy(mfaEnabled = true)

    override suspend fun disableMFA(code: String): User = nextProfile.copy(mfaEnabled = false)

    override fun buildConnectURL(
        provider: SocialProvider,
        redirectURI: String,
    ): String = "https://auth.example.com/v1/auth/connect/${provider.slug}?redirect_uri=$redirectURI"

    override suspend fun socialCallback(
        provider: SocialProvider,
        state: String,
        code: String,
    ): VerifyResponse = VerifyResponse("tkn-social", 900L)

    override suspend fun storeAccessToken(
        token: String,
        expiresAtEpochSec: Long,
    ) {
        tokens += token to expiresAtEpochSec
    }

    override fun readSessionHint(): String? = hint

    override fun writeSessionHint(userId: String) {
        hint = userId
        sessionHints += userId
    }

    override fun clearSessionHint() {
        hint = null
        sessionHints += null
    }

    fun markNewSocialUser(userId: String) {
        newSocialUsers += userId
    }

    override fun isNewSocialUser(userId: String): Boolean = userId in newSocialUsers

    override fun nowEpochSec(): Long = now
}

fun sampleUser(
    id: String = "user-1",
    publicId: String = "alice",
    linked: List<SocialProvider> = emptyList(),
    mfa: Boolean = false,
): User =
    User(
        id = id,
        publicId = publicId,
        displayName = publicId,
        email = "$publicId@example.com",
        iconUrl = null,
        mfaEnabled = mfa,
        mfaVerified = mfa,
        linkedProviders = linked,
        createdAt = "2026-04-01T00:00:00Z",
    )
