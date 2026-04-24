package dev.fuju.feature.admin.domain

import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.UpdateBadgeInput

/**
 * Admin 機能の Repository 抽象。`/v1/admin/badges/{id}` 系を叩く。
 * React 版 `../frontend/src/api/endpoints/admin.ts` を移植。
 */
interface AdminRepository {
    suspend fun listBadges(): List<Badge>
    suspend fun createBadge(input: CreateBadgeInput): Badge
    suspend fun updateBadge(id: String, input: UpdateBadgeInput): Badge
    suspend fun deleteBadge(id: String)
    suspend fun grantBadge(userSub: String, input: GrantBadgeInput)
    suspend fun revokeBadge(userSub: String, badgeKey: String)
}
