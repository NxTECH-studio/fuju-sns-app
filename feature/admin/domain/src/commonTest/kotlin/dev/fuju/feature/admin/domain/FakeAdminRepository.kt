package dev.fuju.feature.admin.domain

import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.domain.UpdateBadgeInput
import kotlinx.coroutines.CompletableDeferred

/**
 * テスト用の `AdminRepository` の in-memory 実装。`FakeProfileRepository` と同じ方針で、
 * 呼び出しの記録 + 応答キュー + 任意の gate を備えた最小の fake。
 */
class FakeAdminRepository : AdminRepository {
    val listBadgesCalls = mutableListOf<Unit>()
    val createBadgeCalls = mutableListOf<CreateBadgeInput>()
    val updateBadgeCalls = mutableListOf<Pair<String, UpdateBadgeInput>>()
    val grantBadgeCalls = mutableListOf<Pair<String, GrantBadgeInput>>()
    val revokeBadgeCalls = mutableListOf<Pair<String, String>>()
    val listUsersCalls = mutableListOf<Pair<Int, Int>>()

    private val listBadgesResponses = ArrayDeque<Result<List<Badge>>>()
    private val createBadgeResponses = ArrayDeque<Result<Badge>>()
    private val updateBadgeResponses = ArrayDeque<Result<Badge>>()
    private val grantBadgeResponses = ArrayDeque<Result<Badge>>()
    private val revokeBadgeResponses = ArrayDeque<Result<Unit>>()
    private val listUsersResponses = ArrayDeque<Result<List<ProfileUser>>>()

    /** grant / revoke の処理を待機させたい場合に使う Deferred。 */
    var grantGate: CompletableDeferred<Unit>? = null
    var revokeGate: CompletableDeferred<Unit>? = null

    fun enqueueListBadges(badges: List<Badge>) {
        listBadgesResponses += Result.success(badges)
    }

    fun enqueueListBadgesError(error: Throwable) {
        listBadgesResponses += Result.failure(error)
    }

    fun enqueueCreateBadge(badge: Badge) {
        createBadgeResponses += Result.success(badge)
    }

    fun enqueueCreateBadgeError(error: Throwable) {
        createBadgeResponses += Result.failure(error)
    }

    fun enqueueUpdateBadge(badge: Badge) {
        updateBadgeResponses += Result.success(badge)
    }

    fun enqueueUpdateBadgeError(error: Throwable) {
        updateBadgeResponses += Result.failure(error)
    }

    fun enqueueGrantBadge(badge: Badge) {
        grantBadgeResponses += Result.success(badge)
    }

    fun enqueueGrantBadgeError(error: Throwable) {
        grantBadgeResponses += Result.failure(error)
    }

    fun enqueueRevokeBadge() {
        revokeBadgeResponses += Result.success(Unit)
    }

    fun enqueueRevokeBadgeError(error: Throwable) {
        revokeBadgeResponses += Result.failure(error)
    }

    fun enqueueListUsers(users: List<ProfileUser>) {
        listUsersResponses += Result.success(users)
    }

    fun enqueueListUsersError(error: Throwable) {
        listUsersResponses += Result.failure(error)
    }

    override suspend fun listBadges(): List<Badge> {
        listBadgesCalls += Unit
        return listBadgesResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected listBadges call")
    }

    override suspend fun createBadge(input: CreateBadgeInput): Badge {
        createBadgeCalls += input
        return createBadgeResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected createBadge call")
    }

    override suspend fun updateBadge(
        id: String,
        input: UpdateBadgeInput,
    ): Badge {
        updateBadgeCalls += id to input
        return updateBadgeResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected updateBadge($id) call")
    }

    override suspend fun grantBadge(
        userSub: String,
        input: GrantBadgeInput,
    ): Badge {
        grantGate?.await()
        grantBadgeCalls += userSub to input
        return grantBadgeResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected grantBadge($userSub) call")
    }

    override suspend fun revokeBadge(
        userSub: String,
        badgeId: String,
    ) {
        revokeGate?.await()
        revokeBadgeCalls += userSub to badgeId
        revokeBadgeResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected revokeBadge($userSub, $badgeId) call")
    }

    override suspend fun listUsers(
        limit: Int,
        offset: Int,
    ): List<ProfileUser> {
        listUsersCalls += limit to offset
        return listUsersResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected listUsers call")
    }
}

internal fun sampleBadge(
    id: String = "badge-1",
    key: String = "supporter",
    label: String = "Supporter",
    description: String = "",
    iconUrl: String = "",
    color: String = "#fff",
    priority: Int = 0,
): Badge =
    Badge(
        id = id,
        key = key,
        label = label,
        description = description,
        iconUrl = iconUrl,
        color = color,
        priority = priority,
    )

internal fun sampleProfileUser(
    sub: String = "user-sub",
    displayName: String = "User",
    displayId: String = "user",
): ProfileUser =
    ProfileUser(
        sub = sub,
        displayName = displayName,
        displayId = displayId,
        iconUrl = "",
        bio = "",
        bannerUrl = "",
        badges = emptyList(),
        createdAt = "2026-04-24T00:00:00Z",
        profileRefreshedAt = "2026-04-24T00:00:00Z",
    )
