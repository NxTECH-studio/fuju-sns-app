package dev.fuju.feature.profile.domain

import app.cash.turbine.test
import dev.fuju.core.domain.FollowResult
import dev.fuju.core.domain.UpdateProfileInput
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @Test
    fun initialLoadForOtherUserFetchesMeAndTargetUser() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeProfileRepository()
            repo.enqueueMe(sampleMe(sub = "me"))
            repo.enqueueUser(sampleUser(sub = "target"))

            val vm =
                ProfileViewModel(
                    repository = repo,
                    targetSub = "target",
                    scope = this,
                )
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals("target", state.user?.sub)
            assertEquals("me", state.me?.sub)
            assertFalse(state.loading)
            assertNull(state.error)
            assertFalse(vm.isSelf)
            assertEquals(listOf("target"), repo.getUserCalls)
        }

    @Test
    fun initialLoadForSelfResolvesSubFromMe() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeProfileRepository()
            repo.enqueueMe(sampleMe(sub = "me"))
            repo.enqueueUser(sampleUser(sub = "me"))

            val vm =
                ProfileViewModel(
                    repository = repo,
                    targetSub = null,
                    scope = this,
                )
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals("me", state.user?.sub)
            assertTrue(vm.isSelf)
            assertEquals(listOf("me"), repo.getUserCalls)
        }

    @Test
    fun syncFollowStateMarksFollowStateKnown() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeProfileRepository()
            repo.enqueueMe(sampleMe(sub = "me"))
            repo.enqueueUser(sampleUser(sub = "target"))
            val vm =
                ProfileViewModel(repository = repo, targetSub = "target", scope = this)
            advanceUntilIdle()

            assertFalse(vm.state.value.followStateKnown)
            vm.syncFollowState(following = true, followersCount = 12)
            assertTrue(vm.state.value.followStateKnown)
            assertTrue(vm.state.value.following)
            assertEquals(12, vm.state.value.followersCount)
        }

    @Test
    fun toggleFollowOptimisticallyFlipsStateAndUsesServerResult() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeProfileRepository()
            repo.enqueueMe(sampleMe(sub = "me"))
            repo.enqueueUser(sampleUser(sub = "target"))
            val gate = CompletableDeferred<Unit>()
            repo.followGate = gate
            repo.enqueueFollow(FollowResult(following = true, followersCount = 8))

            val vm =
                ProfileViewModel(repository = repo, targetSub = "target", scope = this)
            advanceUntilIdle()
            vm.syncFollowState(following = false, followersCount = 7)

            vm.state.test {
                // 初期（not following, count = 7）
                val before = awaitItem()
                assertFalse(before.following)
                assertEquals(7, before.followersCount)

                vm.toggleFollow()
                val optimistic = awaitItem()
                assertTrue(optimistic.following)
                assertEquals(8, optimistic.followersCount)
                assertTrue(optimistic.followPending)

                gate.complete(Unit)
                val afterServer = awaitItem()
                assertTrue(afterServer.following)
                assertEquals(8, afterServer.followersCount)
                assertFalse(afterServer.followPending)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf("target"), repo.followCalls)
        }

    @Test
    fun toggleFollowRollsBackOnFailure() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeProfileRepository()
            repo.enqueueMe(sampleMe(sub = "me"))
            repo.enqueueUser(sampleUser(sub = "target"))
            repo.enqueueFollowError(RuntimeException("boom"))

            val vm =
                ProfileViewModel(repository = repo, targetSub = "target", scope = this)
            advanceUntilIdle()
            vm.syncFollowState(following = false, followersCount = 3)

            vm.toggleFollow()
            advanceUntilIdle()

            val after = vm.state.value
            assertFalse(after.following)
            assertEquals(3, after.followersCount)
            assertFalse(after.followPending)
            assertNotNull(after.error)
        }

    @Test
    fun toggleUnfollowOptimisticallyDecrementsCount() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeProfileRepository()
            repo.enqueueMe(sampleMe(sub = "me"))
            repo.enqueueUser(sampleUser(sub = "target"))
            repo.enqueueUnfollow(FollowResult(following = false, followersCount = 4))
            val vm =
                ProfileViewModel(repository = repo, targetSub = "target", scope = this)
            advanceUntilIdle()
            vm.syncFollowState(following = true, followersCount = 5)

            vm.toggleFollow()
            advanceUntilIdle()

            val after = vm.state.value
            assertFalse(after.following)
            assertEquals(4, after.followersCount)
            assertFalse(after.followPending)
            assertEquals(listOf("target"), repo.unfollowCalls)
        }

    @Test
    fun updateProfileReflectsInState() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeProfileRepository()
            repo.enqueueMe(sampleMe(sub = "me", bio = "old"))
            repo.enqueueUser(sampleUser(sub = "me", bio = "old"))
            repo.enqueueUpdate(sampleMe(sub = "me", bio = "new", bannerUrl = "https://banner"))

            val vm =
                ProfileViewModel(repository = repo, targetSub = null, scope = this)
            advanceUntilIdle()

            val updated =
                vm.updateProfile(UpdateProfileInput(bio = "new", bannerUrl = "https://banner"))
            assertEquals("new", updated.bio)

            val state = vm.state.value
            assertEquals("new", state.me?.bio)
            assertEquals("new", state.user?.bio)
            assertEquals("https://banner", state.user?.bannerUrl)
            assertEquals("me", repo.updateUserCalls.single().first)
        }

    @Test
    fun errorMessageOnFailedInitialLoad() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeProfileRepository()
            repo.enqueueMeError(RuntimeException("network down"))

            val vm =
                ProfileViewModel(repository = repo, targetSub = "target", scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.loading)
            assertNotNull(state.error)
            assertNull(state.user)
        }
}
