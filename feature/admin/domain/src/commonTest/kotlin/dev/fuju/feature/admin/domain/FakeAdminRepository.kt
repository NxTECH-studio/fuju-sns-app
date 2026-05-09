package dev.fuju.feature.admin.domain

import dev.fuju.core.domain.Badge
import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.UpdateBadgeInput

/**
 * テスト用の `AdminRepository` in-memory 実装。`FakeTimelineRepository` 等と同じ方針で
 * 呼び出し記録 + 応答キューを持つ。
 */
class FakeAdminRepository : AdminRepository {
    val listCalls = mutableListOf<Unit>()
    val createCalls = mutableListOf<CreateBadgeInput>()
    val updateCalls = mutableListOf<Pair<String, UpdateBadgeInput>>()
    val deleteCalls = mutableListOf<String>()
    val grantCalls = mutableListOf<Pair<String, GrantBadgeInput>>()
    val revokeCalls = mutableListOf<Pair<String, String>>()

    private val listResponses = ArrayDeque<Result<List<Badge>>>()
    private val createResponses = ArrayDeque<Result<Badge>>()
    private val updateResponses = ArrayDeque<Result<Badge>>()
    private val deleteResponses = ArrayDeque<Result<Unit>>()
    private val grantResponses = ArrayDeque<Result<Badge>>()
    private val revokeResponses = ArrayDeque<Result<Unit>>()

    fun enqueueList(badges: List<Badge>) {
        listResponses += Result.success(badges)
    }

    fun enqueueListError(error: Throwable) {
        listResponses += Result.failure(error)
    }

    fun enqueueCreate(badge: Badge) {
        createResponses += Result.success(badge)
    }

    fun enqueueUpdate(badge: Badge) {
        updateResponses += Result.success(badge)
    }

    fun enqueueDelete() {
        deleteResponses += Result.success(Unit)
    }

    fun enqueueGrant(badge: Badge) {
        grantResponses += Result.success(badge)
    }

    fun enqueueRevoke() {
        revokeResponses += Result.success(Unit)
    }

    override suspend fun listBadges(): List<Badge> {
        listCalls += Unit
        return listResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected listBadges call")
    }

    override suspend fun createBadge(input: CreateBadgeInput): Badge {
        createCalls += input
        return createResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected createBadge call")
    }

    override suspend fun updateBadge(
        id: String,
        input: UpdateBadgeInput,
    ): Badge {
        updateCalls += id to input
        return updateResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected updateBadge call")
    }

    override suspend fun deleteBadge(id: String) {
        deleteCalls += id
        deleteResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected deleteBadge call")
    }

    override suspend fun grantBadge(
        userSub: String,
        input: GrantBadgeInput,
    ): Badge {
        grantCalls += userSub to input
        return grantResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected grantBadge call")
    }

    override suspend fun revokeBadge(
        userSub: String,
        badgeId: String,
    ) {
        revokeCalls += userSub to badgeId
        revokeResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected revokeBadge call")
    }
}

internal fun sampleBadge(
    id: String = "badge-1",
    key: String = "early",
    label: String = "Early Adopter",
    description: String = "初期参加",
    iconUrl: String = "",
    color: String = "#FFD700",
    priority: Int = 10,
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
