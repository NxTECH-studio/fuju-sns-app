package dev.fuju.feature.auth.data

import dev.fuju.core.domain.MFASetupResult
import dev.fuju.core.domain.SocialProvider
import dev.fuju.core.domain.User
import dev.fuju.core.error.AuthException
import dev.fuju.core.error.toAuthException
import dev.fuju.core.network.throwIfError
import dev.fuju.core.storage.SessionHintStore
import dev.fuju.core.storage.TokenStorage
import dev.fuju.feature.auth.data.dto.DisableMFARequestDto
import dev.fuju.feature.auth.data.dto.EnableMFARequestDto
import dev.fuju.feature.auth.data.dto.LoginRequestDto
import dev.fuju.feature.auth.data.dto.LoginResponseDto
import dev.fuju.feature.auth.data.dto.MFARegisterResponseDto
import dev.fuju.feature.auth.data.dto.RefreshResponseDto
import dev.fuju.feature.auth.data.dto.RegisterRequestDto
import dev.fuju.feature.auth.data.dto.RegisterResponseDto
import dev.fuju.feature.auth.data.dto.SocialCallbackRequestDto
import dev.fuju.feature.auth.data.dto.UpdatePublicIdRequestDto
import dev.fuju.feature.auth.data.dto.UserProfileResponseDto
import dev.fuju.feature.auth.data.dto.VerifyMFARequestDto
import dev.fuju.feature.auth.data.dto.VerifyMFAResponseDto
import dev.fuju.feature.auth.domain.AuthActions
import dev.fuju.feature.auth.domain.LoginResponse
import dev.fuju.feature.auth.domain.RefreshResponse
import dev.fuju.feature.auth.domain.VerifyResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/**
 * AuthCore `/v1/auth/*` + `/v1/user/*` を叩く Repository。
 * [AuthActions] を実装して state machine に接続する。
 *
 * `../auth-component/src/store/AuthStore.ts` が直接行っていた I/O を、KMP 側では
 * state machine (:feature:auth:domain) と切り離して、ここに集約する。
 */
class AuthRepository(
    private val client: HttpClient,
    private val tokenStorage: TokenStorage,
    private val sessionHint: SessionHintStore,
    private val clock: () -> Long = { nowSeconds() },
) : AuthActions {

    override suspend fun login(identifier: String, password: String): LoginResponse = wrap {
        val res: LoginResponseDto = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequestDto(identifier, password))
        }.also { it.throwIfError() }.body()

        if (res.mfaRequired) {
            LoginResponse(mfaRequired = true, preToken = res.preToken)
        } else {
            LoginResponse(
                mfaRequired = false,
                accessToken = res.accessToken,
                expiresInSec = res.expiresIn,
            )
        }
    }

    override suspend fun register(email: String, password: String, publicId: String): User = wrap {
        val res: RegisterResponseDto = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequestDto(email, password, publicId))
        }.also { it.throwIfError() }.body()
        res.toDomain()
    }

    override suspend fun logout() {
        runCatching {
            client.post("/v1/auth/logout").throwIfError()
        }
    }

    override suspend fun refresh(): RefreshResponse = wrap {
        val res: RefreshResponseDto = client.post("/v1/auth/refresh")
            .also { it.throwIfError() }
            .body()
        RefreshResponse(res.accessToken, res.expiresIn)
    }

    override suspend fun verifyMFA(preToken: String, code: String?, recoveryCode: String?): VerifyResponse = wrap {
        val res: VerifyMFAResponseDto = client.post("/v1/auth/mfa/verify") {
            header(HttpHeaders.Authorization, "Bearer $preToken")
            contentType(ContentType.Application.Json)
            setBody(VerifyMFARequestDto(code = code, recoveryCode = recoveryCode))
        }.also { it.throwIfError() }.body()
        VerifyResponse(res.accessToken, res.expiresIn)
    }

    override suspend fun loadProfile(): User = wrap {
        val res: UserProfileResponseDto = client.get("/v1/user/profile")
            .also { it.throwIfError() }
            .body()
        res.toDomain()
    }

    override suspend fun updatePublicId(next: String): User = wrap {
        client.patch("/v1/user/public_id") {
            contentType(ContentType.Application.Json)
            setBody(UpdatePublicIdRequestDto(next))
        }.throwIfError()
        loadProfile()
    }

    override suspend fun setupMFA(): MFASetupResult = wrap {
        val res: MFARegisterResponseDto = client.post("/v1/auth/mfa/register")
            .also { it.throwIfError() }
            .body()
        MFASetupResult(res.secret, res.qrCode, res.recoveryCodes)
    }

    override suspend fun enableMFA(code: String): User = wrap {
        client.post("/v1/auth/mfa/enable") {
            contentType(ContentType.Application.Json)
            setBody(EnableMFARequestDto(code))
        }.throwIfError()
        loadProfile()
    }

    override suspend fun disableMFA(code: String): User = wrap {
        client.post("/v1/auth/mfa/disable") {
            contentType(ContentType.Application.Json)
            setBody(DisableMFARequestDto(code))
        }.throwIfError()
        loadProfile()
    }

    override fun buildConnectURL(provider: SocialProvider, redirectURI: String): String {
        val encoded = urlEncode(redirectURI)
        return "/v1/auth/connect/${provider.slug}?redirect_uri=$encoded"
    }

    override suspend fun socialCallback(provider: SocialProvider, state: String, code: String): VerifyResponse = wrap {
        val res: VerifyMFAResponseDto = client.post("/v1/auth/callback/${provider.slug}") {
            contentType(ContentType.Application.Json)
            setBody(SocialCallbackRequestDto(state = state, code = code))
        }.also { it.throwIfError() }.body()
        VerifyResponse(res.accessToken, res.expiresIn)
    }

    suspend fun disconnectSocial(provider: SocialProvider, providerUserId: String): User = wrap {
        client.delete("/v1/auth/disconnect/${provider.slug}/$providerUserId").throwIfError()
        loadProfile()
    }

    override suspend fun storeAccessToken(token: String, expiresAtEpochSec: Long) {
        tokenStorage.setToken(token, expiresAtEpochSec)
    }

    override fun readSessionHint(): String? = sessionHint.read()
    override fun writeSessionHint(userId: String) { sessionHint.write(userId) }
    override fun clearSessionHint() { sessionHint.clear() }

    override fun isNewSocialUser(userId: String): Boolean = false
    override fun nowEpochSec(): Long = clock()

    private inline fun <T> wrap(block: () -> T): T =
        try {
            block()
        } catch (e: AuthException) {
            throw e
        } catch (t: Throwable) {
            throw t.toAuthException()
        }
}

internal expect fun nowSeconds(): Long
internal expect fun urlEncode(value: String): String
