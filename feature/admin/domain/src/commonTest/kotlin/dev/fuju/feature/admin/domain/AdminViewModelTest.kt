package dev.fuju.feature.admin.domain

import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.GrantBadgeInput
import dev.fuju.core.domain.UpdateBadgeInput
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {
    @Test
    fun initialLoadFetchesBadgesAndUsersSortedByPriorityDesc() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueListBadges(
                listOf(
                    sampleBadge(id = "low", priority = 1),
                    sampleBadge(id = "high", priority = 10),
                    sampleBadge(id = "mid", priority = 5),
                ),
            )
            repo.enqueueListUsers(
                listOf(sampleProfileUser(sub = "u1"), sampleProfileUser(sub = "u2")),
            )

            val vm = AdminViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(listOf("high", "mid", "low"), state.badges.map { it.id })
            assertEquals(listOf("u1", "u2"), state.users.map { it.sub })
            assertFalse(state.badgesLoading)
            assertFalse(state.usersLoading)
            assertNull(state.error)
        }

    @Test
    fun filterUsersMatchesDisplayNameCaseInsensitive() {
        val users =
            listOf(
                sampleProfileUser(sub = "1", displayName = "Alice", displayId = "alice"),
                sampleProfileUser(sub = "2", displayName = "Bob", displayId = "bobby"),
                sampleProfileUser(sub = "3", displayName = "ALAN", displayId = "alan"),
            )

            // 大文字小文字を無視した部分一致
        assertEquals(
            listOf("1", "3"),
            filterUsers(users, "al").map { it.sub },
        )
            // displayId にもマッチする
        assertEquals(
            listOf("2"),
            filterUsers(users, "BOBBY").map { it.sub },
        )
            // sub にもマッチする
        assertEquals(
            listOf("1"),
            filterUsers(users, "1").map { it.sub },
        )
            // 空クエリは全件
        assertEquals(3, filterUsers(users, "  ").size)
            // 該当なし
        assertEquals(0, filterUsers(users, "zz").size)
    }

    @Test
    fun createBadgeAppendsToStateAndResorts() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueListBadges(listOf(sampleBadge(id = "b1", priority = 5)))
            repo.enqueueListUsers(emptyList())
            val vm = AdminViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            repo.enqueueCreateBadge(sampleBadge(id = "b2", key = "k2", priority = 9))
            val created =
                vm.createBadge(
                    CreateBadgeInput(
                        key = "k2",
                        label = "Two",
                        color = "#aaa",
                        priority = 9,
                    ),
                )
            advanceUntilIdle()

            assertEquals("b2", created.id)
            assertEquals(listOf("b2", "b1"), vm.state.value.badges.map { it.id })
            assertEquals(1, repo.createBadgeCalls.size)
        }

    @Test
    fun updateBadgeReplacesByIdAndResorts() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueListBadges(
                listOf(
                    sampleBadge(id = "b1", priority = 5, label = "old"),
                    sampleBadge(id = "b2", priority = 1),
                ),
            )
            repo.enqueueListUsers(emptyList())
            val vm = AdminViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            // priority を上げて新しいラベルで更新
            repo.enqueueUpdateBadge(sampleBadge(id = "b1", priority = 0, label = "new"))
            val updated =
                vm.updateBadge(
                    id = "b1",
                    input = UpdateBadgeInput(label = "new", priority = 0),
                )
            advanceUntilIdle()

            assertEquals("new", updated.label)
            // priority 1 (b2) > 0 (b1) なので並び順が入れ替わる
            assertEquals(listOf("b2", "b1"), vm.state.value.badges.map { it.id })
            assertEquals("new", vm.state.value.badges[1].label)
        }

    @Test
    fun createBadgeReportsSanitizedErrorOnFailure() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueListBadges(emptyList())
            repo.enqueueListUsers(emptyList())
            val vm = AdminViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            repo.enqueueCreateBadgeError(RuntimeException("raw boom"))
            try {
                vm.createBadge(
                    CreateBadgeInput(key = "k", label = "L", color = "#fff", priority = 0),
                )
                fail("expected createBadge to rethrow")
            } catch (t: Throwable) {
                assertEquals("raw boom", t.message)
            }
            assertNotNull(vm.state.value.error)
            // raw な例外メッセージがそのまま UI に出ないこと
            assertFalse(vm.state.value.error!!.contains("raw boom"))
        }

    @Test
    fun grantBadgeOptimisticallyCallbacksWithServerBadge() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueListBadges(emptyList())
            repo.enqueueListUsers(emptyList())
            val vm = AdminViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            repo.grantGate = gate
            repo.enqueueGrantBadge(sampleBadge(id = "g1", key = "k", label = "Granted"))

            var result: Result<dev.fuju.core.domain.Badge>? = null
            vm.grantBadge(
                userSub = "user-1",
                input = GrantBadgeInput(badgeKey = "k"),
                onResult = { result = it },
            )
            // gate が開く前は callback されていない
            advanceUntilIdle()
            assertNull(result)

            gate.complete(Unit)
            advanceUntilIdle()

            assertNotNull(result)
            assertTrue(result!!.isSuccess)
            assertEquals("g1", result!!.getOrNull()?.id)
            assertEquals(listOf("user-1" to GrantBadgeInput(badgeKey = "k")), repo.grantBadgeCalls)
        }

    @Test
    fun grantBadgeDuplicateForSameUserBadgeCancelsPriorJob() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueListBadges(emptyList())
            repo.enqueueListUsers(emptyList())
            val vm = AdminViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            repo.grantGate = gate
            // 1 回目用と 2 回目用の応答をキューに積む
            repo.enqueueGrantBadge(sampleBadge(id = "g1"))
            repo.enqueueGrantBadge(sampleBadge(id = "g2"))

            var firstResult: Result<dev.fuju.core.domain.Badge>? = null
            var secondResult: Result<dev.fuju.core.domain.Badge>? = null

            vm.grantBadge(
                userSub = "user-1",
                input = GrantBadgeInput(badgeKey = "k"),
                onResult = { firstResult = it },
            )
            vm.grantBadge(
                userSub = "user-1",
                input = GrantBadgeInput(badgeKey = "k"),
                onResult = { secondResult = it },
            )

            gate.complete(Unit)
            advanceUntilIdle()

            // 1 回目は cancel されたので callback されない
            assertNull(firstResult)
            assertNotNull(secondResult)
            assertTrue(secondResult!!.isSuccess)
        }

    @Test
    fun revokeBadgeReportsSuccess() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueListBadges(emptyList())
            repo.enqueueListUsers(emptyList())
            val vm = AdminViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            repo.enqueueRevokeBadge()

            var result: Result<Unit>? = null
            vm.revokeBadge(userSub = "user-1", badgeId = "b1") { result = it }
            advanceUntilIdle()

            assertNotNull(result)
            assertTrue(result!!.isSuccess)
            assertEquals(listOf("user-1" to "b1"), repo.revokeBadgeCalls)
        }

    @Test
    fun revokeBadgeReportsSanitizedErrorOnFailure() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueListBadges(emptyList())
            repo.enqueueListUsers(emptyList())
            val vm = AdminViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            repo.enqueueRevokeBadgeError(RuntimeException("raw"))

            var result: Result<Unit>? = null
            vm.revokeBadge(userSub = "user-1", badgeId = "b1") { result = it }
            advanceUntilIdle()

            assertNotNull(result)
            assertTrue(result!!.isFailure)
            assertNotNull(vm.state.value.error)
        }

    @Test
    fun reloadBadgesSetsErrorOnFailure() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueListBadgesError(RuntimeException("net"))
            repo.enqueueListUsers(emptyList())
            val vm = AdminViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.badgesLoading)
            assertNotNull(state.error)
        }
}
