package dev.fuju.feature.auth.data

import dev.fuju.core.domain.SocialProvider
import dev.fuju.core.domain.User
import dev.fuju.feature.auth.data.dto.RegisterResponseDto
import dev.fuju.feature.auth.data.dto.UserProfileResponseDto

internal fun UserProfileResponseDto.toDomain(): User =
    User(
        id = id,
        publicId = publicId,
        displayName = displayName ?: publicId,
        email = email,
        iconUrl = iconUrl,
        mfaEnabled = mfaEnabled,
        mfaVerified = mfaVerified ?: mfaEnabled,
        linkedProviders = linkedProviders.orEmpty().mapNotNull(SocialProvider::fromSlug),
        createdAt = createdAt,
    )

internal fun RegisterResponseDto.toDomain(): User =
    User(
        id = id,
        publicId = publicId,
        displayName = publicId,
        email = email,
        iconUrl = iconUrl,
        mfaEnabled = mfaEnabled,
        mfaVerified = false,
        linkedProviders = emptyList(),
        createdAt = createdAt,
    )
