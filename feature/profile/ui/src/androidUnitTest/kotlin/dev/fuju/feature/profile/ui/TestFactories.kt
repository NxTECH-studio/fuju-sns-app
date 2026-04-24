package dev.fuju.feature.profile.ui

import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.ProfileUser

/**
 * Screenshot test 専用のダミーデータビルダー。
 * prod bundle に漏らさないため、profile:ui の `androidUnitTest` に閉じて置く。
 */
internal object TestFactories {
    fun fakeBadge(
        id: String = "badge-1",
        key: String = "early",
        label: String = "Early Adopter",
        priority: Int = 10,
    ): Badge =
        Badge(
            id = id,
            key = key,
            label = label,
            description = "初期から参加してくれたユーザー",
            iconUrl = "",
            color = "#FFD700",
            priority = priority,
        )

    fun fakeProfileUser(
        sub: String = "user-001",
        displayName: String = "ふじ花子",
        displayId: String = "hanako",
        bio: String = "KMP / Compose を触りながら SNS を作っています。",
        badges: List<Badge> = listOf(fakeBadge()),
    ): ProfileUser =
        ProfileUser(
            sub = sub,
            displayName = displayName,
            displayId = displayId,
            iconUrl = "",
            bio = bio,
            bannerUrl = "",
            badges = badges,
            createdAt = "2026-01-01T00:00:00Z",
            profileRefreshedAt = "2026-04-24T10:00:00Z",
        )
}
