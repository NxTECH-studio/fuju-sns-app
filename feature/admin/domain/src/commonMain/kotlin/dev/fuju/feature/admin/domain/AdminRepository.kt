package dev.fuju.feature.admin.domain

import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.domain.UpdateBadgeInput

/**
 * Admin 機能の Repository 抽象。`/v1/admin/badges/{id}` 系と `/users` を叩く。
 * React 版 `../frontend/src/api/endpoints/admin.ts` を移植。
 *
 * ## ユーザー検索
 * Backend には search API が存在しないため、`listUsers` で全件 (or 1 ページ) を取得し、
 * 呼び出し側 (ViewModel) でクライアントサイドフィルタする方針。
 *
 * ## Badge の削除
 * Backend swagger には `DELETE /v1/admin/badges/{id}` が **無い**ため、本リポジトリでは
 * `deleteBadge` を提供しない。バッジ自体の論理削除は backend 側で別途検討する。
 *
 * ## Revoke
 * `/v1/admin/users/{sub}/badges/{badge_id}` は **badge_id (ULID)** を受け取る。
 * `badge_key` ではない。React 版 `adminBadgesRevoke(sub, badgeId)` と同じシグネチャ。
 */
interface AdminRepository {
    suspend fun listBadges(): List<Badge>

    suspend fun createBadge(input: CreateBadgeInput): Badge

    suspend fun updateBadge(
        id: String,
        input: UpdateBadgeInput,
    ): Badge

    /**
     * 指定ユーザーにバッジを付与する。成功時は付与されたバッジ本体を返す。
     */
    suspend fun grantBadge(
        userSub: String,
        input: GrantBadgeInput,
    ): Badge

    /**
     * 指定ユーザーから badge を剥奪する。idempotent。
     */
    suspend fun revokeBadge(
        userSub: String,
        badgeId: String,
    )

    /**
     * Admin の "ユーザー検索" 用に全件 (or 1 ページ) を取得する。React 版 `useUsers` 相当。
     * Backend には search API が無いため、呼び出し側で [ProfileUser.displayName] /
     * [ProfileUser.displayId] / [ProfileUser.sub] に対して client-side フィルタする。
     */
    suspend fun listUsers(
        limit: Int = DEFAULT_USERS_LIMIT,
        offset: Int = 0,
    ): List<ProfileUser>

    companion object {
        const val DEFAULT_USERS_LIMIT: Int = 100
    }
}
