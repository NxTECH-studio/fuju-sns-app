package dev.fuju.feature.auth.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AuthCore の wire format（snake_case）。
 * Kotlin 側は camelCase の domain 型に写像する。
 */
@Serializable
data class LoginRequestDto(
    val identifier: String,
    val password: String,
)

@Serializable
data class LoginResponseDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0L,
    @SerialName("mfa_required") val mfaRequired: Boolean = false,
    @SerialName("pre_token") val preToken: String? = null,
)

@Serializable
data class RefreshResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    @SerialName("public_id") val publicId: String,
)

@Serializable
data class RegisterResponseDto(
    val id: String,
    val email: String,
    @SerialName("public_id") val publicId: String,
    @SerialName("mfa_enabled") val mfaEnabled: Boolean,
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class VerifyMFARequestDto(
    val code: String? = null,
    @SerialName("recovery_code") val recoveryCode: String? = null,
)

@Serializable
data class VerifyMFAResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
data class MFARegisterResponseDto(
    val secret: String,
    @SerialName("qr_code") val qrCode: String,
    @SerialName("recovery_codes") val recoveryCodes: List<String>,
)

@Serializable
data class EnableMFARequestDto(val code: String)
@Serializable
data class DisableMFARequestDto(val code: String)

@Serializable
data class SocialCallbackRequestDto(
    val state: String,
    val code: String,
    @SerialName("redirect_uri") val redirectUri: String? = null,
)

@Serializable
data class UserProfileResponseDto(
    val id: String,
    val email: String,
    @SerialName("public_id") val publicId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("mfa_enabled") val mfaEnabled: Boolean,
    @SerialName("mfa_verified") val mfaVerified: Boolean? = null,
    @SerialName("linked_providers") val linkedProviders: List<String>? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class UpdatePublicIdRequestDto(
    @SerialName("public_id") val publicId: String,
)
