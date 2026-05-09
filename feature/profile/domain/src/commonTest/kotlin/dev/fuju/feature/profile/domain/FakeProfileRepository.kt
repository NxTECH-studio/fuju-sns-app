package dev.fuju.feature.profile.domain

import dev.fuju.core.domain.FollowResult
import dev.fuju.core.domain.Me
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.domain.UpdateProfileInput
import kotlinx.coroutines.CompletableDeferred

/**
 * テスト用の `ProfileRepository` in-memory 実装。`FakeTimelineRepository` と同じ方針で
 * 呼び出しの記録 + 応答キュー + 任意の gate を備えた最小の fake。
 */
class FakeProfileRepository : ProfileRepository {
    val getMeCalls = mutableListOf<Unit>()
    val listUsersCalls = mutableListOf<Pair<Int, Int>>()
    val getUserCalls = mutableListOf<String>()
    val updateUserCalls = mutableListOf<Pair<String, UpdateProfileInput>>()
    val followCalls = mutableListOf<String>()
    val unfollowCalls = mutableListOf<String>()
    val followersCalls = mutableListOf<Pair<String, FollowListQuery>>()
    val followingCalls = mutableListOf<Pair<String, FollowListQuery>>()

    private val meResponses = ArrayDeque<Result<Me>>()
    private val userResponses = ArrayDeque<Result<ProfileUser>>()
    private val followResponses = ArrayDeque<Result<FollowResult>>()
    private val unfollowResponses = ArrayDeque<Result<FollowResult>>()
    private val followersResponses = ArrayDeque<Result<FollowListPage>>()
    private val followingResponses = ArrayDeque<Result<FollowListPage>>()
    private val updateResponses = ArrayDeque<Result<Me>>()

    /** follow / unfollow の処理を待機させたい場合に使う Deferred。 */
    var followGate: CompletableDeferred<Unit>? = null

    fun enqueueMe(me: Me) {
        meResponses += Result.success(me)
    }

    fun enqueueMeError(error: Throwable) {
        meResponses += Result.failure(error)
    }

    fun enqueueUser(user: ProfileUser) {
        userResponses += Result.success(user)
    }

    fun enqueueUserError(error: Throwable) {
        userResponses += Result.failure(error)
    }

    fun enqueueFollow(result: FollowResult) {
        followResponses += Result.success(result)
    }

    fun enqueueFollowError(error: Throwable) {
        followResponses += Result.failure(error)
    }

    fun enqueueUnfollow(result: FollowResult) {
        unfollowResponses += Result.success(result)
    }

    fun enqueueUnfollowError(error: Throwable) {
        unfollowResponses += Result.failure(error)
    }

    fun enqueueFollowers(page: FollowListPage) {
        followersResponses += Result.success(page)
    }

    fun enqueueFollowing(page: FollowListPage) {
        followingResponses += Result.success(page)
    }

    fun enqueueUpdate(me: Me) {
        updateResponses += Result.success(me)
    }

    override suspend fun getMe(): Me {
        getMeCalls += Unit
        return meResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected getMe call")
    }

    override suspend fun listUsers(
        limit: Int,
        offset: Int,
    ): UserListPage {
        listUsersCalls += limit to offset
        return UserListPage(items = emptyList(), limit = limit, offset = offset, total = 0)
    }

    override suspend fun getUser(sub: String): ProfileUser {
        getUserCalls += sub
        return userResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected getUser($sub) call")
    }

    override suspend fun updateUser(
        sub: String,
        input: UpdateProfileInput,
    ): Me {
        updateUserCalls += sub to input
        return updateResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected updateUser($sub) call")
    }

    override suspend fun follow(sub: String): FollowResult {
        followGate?.await()
        followCalls += sub
        return followResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected follow($sub) call")
    }

    override suspend fun unfollow(sub: String): FollowResult {
        followGate?.await()
        unfollowCalls += sub
        return unfollowResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected unfollow($sub) call")
    }

    override suspend fun followers(
        sub: String,
        query: FollowListQuery,
    ): FollowListPage {
        followersCalls += sub to query
        return followersResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected followers($sub) call")
    }

    override suspend fun following(
        sub: String,
        query: FollowListQuery,
    ): FollowListPage {
        followingCalls += sub to query
        return followingResponses.removeFirstOrNull()?.getOrThrow()
            ?: error("unexpected following($sub) call")
    }
}

internal fun sampleMe(
    sub: String = "me-sub",
    displayName: String = "Me",
    displayId: String = "me",
    bio: String = "",
    bannerUrl: String = "",
    isAdmin: Boolean = false,
): Me =
    Me(
        sub = sub,
        displayName = displayName,
        displayId = displayId,
        iconUrl = "",
        bio = bio,
        bannerUrl = bannerUrl,
        badges = emptyList(),
        createdAt = "2026-04-24T00:00:00Z",
        profileRefreshedAt = "2026-04-24T00:00:00Z",
        isAdmin = isAdmin,
    )

internal fun sampleUser(
    sub: String = "user-sub",
    displayName: String = "User",
    displayId: String = "user",
    bio: String = "",
    bannerUrl: String = "",
): ProfileUser =
    ProfileUser(
        sub = sub,
        displayName = displayName,
        displayId = displayId,
        iconUrl = "",
        bio = bio,
        bannerUrl = bannerUrl,
        badges = emptyList(),
        createdAt = "2026-04-24T00:00:00Z",
        profileRefreshedAt = "2026-04-24T00:00:00Z",
    )
