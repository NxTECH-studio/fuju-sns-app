package dev.fuju.feature.admin.domain

import dev.fuju.core.domain.FollowResult
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.Me
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.domain.UpdateProfileInput
import dev.fuju.feature.profile.domain.FollowListPage
import dev.fuju.feature.profile.domain.FollowListQuery
import dev.fuju.feature.profile.domain.ProfileRepository
import dev.fuju.feature.profile.domain.UserListPage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AdminUserBadgesViewModelTest {
    @Test
    fun initialLoadFetchesFirstPage() =
        runTest(StandardTestDispatcher()) {
            val profile =
                StubProfileRepository(
                    listResponses =
                        ArrayDeque(
                            listOf(
                                UserListPage(
                                    items = listOf(sampleProfileUser(sub = "u1")),
                                    limit = 20,
                                    offset = 0,
                                    total = 1,
                                ),
                            ),
                        ),
                )
            val admin = FakeAdminRepository()

            val vm = AdminUserBadgesViewModel(adminRepository = admin, profileRepository = profile, scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(listOf("u1"), state.users.map { it.sub })
            assertFalse(state.usersLoading)
            assertNull(state.usersError)
        }

    @Test
    fun selectTargetLoadsUserAndStoresIt() =
        runTest(StandardTestDispatcher()) {
            val profile =
                StubProfileRepository(
                    listResponses =
                        ArrayDeque(
                            listOf(UserListPage(items = emptyList(), limit = 20, offset = 0, total = 0)),
                        ),
                    userResponses = ArrayDeque(listOf(sampleProfileUser(sub = "u1", displayName = "Alice"))),
                )
            val admin = FakeAdminRepository()

            val vm = AdminUserBadgesViewModel(adminRepository = admin, profileRepository = profile, scope = this)
            advanceUntilIdle()

            vm.selectTarget("u1")
            advanceUntilIdle()

            val target = vm.state.value.targetUser
            assertEquals("Alice", target?.displayName)
        }

    @Test
    fun grantRefreshesTargetUser() =
        runTest(StandardTestDispatcher()) {
            val profile =
                StubProfileRepository(
                    listResponses =
                        ArrayDeque(
                            listOf(UserListPage(items = emptyList(), limit = 20, offset = 0, total = 0)),
                        ),
                    userResponses =
                        ArrayDeque(
                            listOf(
                                sampleProfileUser(sub = "u1"),
                                sampleProfileUser(sub = "u1"), // refresh after grant
                            ),
                        ),
                )
            val admin = FakeAdminRepository()
            admin.enqueueGrant(sampleBadge(id = "b1", key = "early"))

            val vm = AdminUserBadgesViewModel(adminRepository = admin, profileRepository = profile, scope = this)
            advanceUntilIdle()
            vm.selectTarget("u1")
            advanceUntilIdle()

            vm.grant(GrantBadgeInput(badgeKey = "early"))
            advanceUntilIdle()

            val grantCall = admin.grantCalls.single()
            assertEquals("u1", grantCall.first)
            assertEquals("early", grantCall.second.badgeKey)
            assertFalse(vm.state.value.grantPending)
        }

    @Test
    fun revokeReachesRepositoryWithBadgeId() =
        runTest(StandardTestDispatcher()) {
            val profile =
                StubProfileRepository(
                    listResponses =
                        ArrayDeque(
                            listOf(UserListPage(items = emptyList(), limit = 20, offset = 0, total = 0)),
                        ),
                    userResponses =
                        ArrayDeque(
                            listOf(
                                sampleProfileUser(sub = "u1"),
                                sampleProfileUser(sub = "u1"),
                            ),
                        ),
                )
            val admin = FakeAdminRepository()
            admin.enqueueRevoke()

            val vm = AdminUserBadgesViewModel(adminRepository = admin, profileRepository = profile, scope = this)
            advanceUntilIdle()
            vm.selectTarget("u1")
            advanceUntilIdle()

            vm.revoke(badgeId = "b1")
            advanceUntilIdle()

            assertEquals("u1" to "b1", admin.revokeCalls.single())
        }

    @Test
    fun usersErrorIsExposedToState() =
        runTest(StandardTestDispatcher()) {
            val profile =
                StubProfileRepository(
                    listError = RuntimeException("boom"),
                )
            val admin = FakeAdminRepository()

            val vm = AdminUserBadgesViewModel(adminRepository = admin, profileRepository = profile, scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state.users.isEmpty())
            assertNotNull(state.usersError)
        }
}

private fun sampleProfileUser(
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

/**
 * Test 用に単純化した ProfileRepository。VM が呼ぶ listUsers / getUser のみ実装し、
 * その他のメソッドは TODO で fail させる。
 */
private class StubProfileRepository(
    private val listResponses: ArrayDeque<UserListPage> = ArrayDeque(),
    private val userResponses: ArrayDeque<ProfileUser> = ArrayDeque(),
    private val listError: Throwable? = null,
) : ProfileRepository {
    override suspend fun listUsers(
        limit: Int,
        offset: Int,
    ): UserListPage {
        listError?.let { throw it }
        return listResponses.removeFirstOrNull() ?: error("listUsers no response")
    }

    override suspend fun getUser(sub: String): ProfileUser =
        userResponses.removeFirstOrNull() ?: error("getUser($sub) no response")

    override suspend fun getMe(): Me = error("not used in tests")

    override suspend fun updateUser(
        sub: String,
        input: UpdateProfileInput,
    ): Me = error("not used in tests")

    override suspend fun follow(sub: String): FollowResult = error("not used in tests")

    override suspend fun unfollow(sub: String): FollowResult = error("not used in tests")

    override suspend fun followers(
        sub: String,
        query: FollowListQuery,
    ): FollowListPage = error("not used in tests")

    override suspend fun following(
        sub: String,
        query: FollowListQuery,
    ): FollowListPage = error("not used in tests")
}
