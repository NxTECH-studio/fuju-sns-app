package dev.fuju.feature.auth.ui

import dev.fuju.core.domain.LoginResult
import dev.fuju.core.domain.SocialProvider
import dev.fuju.core.domain.User
import dev.fuju.core.error.AuthException
import dev.fuju.core.error.ErrorCode

/**
 * Screenshot test 専用のダミーデータビルダー。
 * テストで組み立てる state を prod bundle に漏らさないため、各 UI モジュールの
 * `androidUnitTest` に閉じて置く。
 */
internal object TestFactories {
    fun fakeUser(
        id: String = "user-001",
        publicId: String = "hanako",
        displayName: String = "ふじ花子",
        email: String = "hanako@example.com",
    ): User =
        User(
            id = id,
            publicId = publicId,
            displayName = displayName,
            email = email,
            iconUrl = null,
            mfaEnabled = false,
            mfaVerified = false,
            linkedProviders = emptyList(),
            createdAt = "2026-01-01T00:00:00Z",
        )

    /**
     * LoginForm の `onLogin` スタブ。成功レスポンスを即返す。
     */
    val successfulLogin: suspend (String, String) -> LoginResult = { _, _ ->
        LoginResult.Authenticated(fakeUser())
    }

    /**
     * LoginForm の `onLogin` スタブ。INVALID_CREDENTIALS を投げる。
     */
    val failingLogin: suspend (String, String) -> LoginResult = { _, _ ->
        throw AuthException(
            code = ErrorCode.INVALID_CREDENTIALS,
            status = 401,
            message = "invalid credentials",
        )
    }

    val defaultProviders: List<SocialProvider> =
        listOf(SocialProvider.GOOGLE, SocialProvider.X, SocialProvider.TWITCH)
}
