package dev.fuju.feature.admin.domain

import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.UpdateBadgeInput

/**
 * Admin 機能の Repository 抽象。`/v1/admin/badges/{id}` 系を叩く。
 * frontend `../frontend/src/api/endpoints/admin.ts` を移植。
 *
 * grant は `Badge` を返す。revoke は無応答 (204) で返ることを期待する。
 */
interface AdminRepository {
    suspend fun listBadges(): List<Badge>

    suspend fun createBadge(input: CreateBadgeInput): Badge

    suspend fun updateBadge(
        id: String,
        input: UpdateBadgeInput,
    ): Badge

    suspend fun deleteBadge(id: String)

    suspend fun grantBadge(
        userSub: String,
        input: GrantBadgeInput,
    ): Badge

    /**
     * @param badgeId 付与済みバッジの id（ULID）。
     *   frontend のエンドポイント `DELETE /v1/admin/users/{sub}/badges/{badgeId}` に揃える。
     */
    suspend fun revokeBadge(
        userSub: String,
        badgeId: String,
    )
}
