package dev.fuju.feature.admin.domain

import dev.fuju.core.domain.CreateBadgeInput
import dev.fuju.core.domain.UpdateBadgeInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AdminBadgesViewModelTest {
    @Test
    fun initialLoadFetchesBadges() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueList(listOf(sampleBadge(id = "b1"), sampleBadge(id = "b2", key = "vip")))

            val vm = AdminBadgesViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(listOf("b1", "b2"), state.badges.map { it.id })
            assertFalse(state.loading)
            assertNull(state.error)
        }

    @Test
    fun createAppendsBadge() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueList(emptyList())
            repo.enqueueCreate(sampleBadge(id = "new", key = "hero"))

            val vm = AdminBadgesViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            vm.create(CreateBadgeInput(key = "hero", label = "Hero", color = "#000", priority = 1))
            advanceUntilIdle()

            assertEquals(
                listOf("new"),
                vm.state.value.badges
                    .map { it.id },
            )
            assertEquals(
                "hero",
                repo.createCalls
                    .single()
                    .key,
            )
        }

    @Test
    fun updateReplacesBadgeById() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueList(listOf(sampleBadge(id = "b1", label = "old"), sampleBadge(id = "b2", key = "vip")))
            repo.enqueueUpdate(sampleBadge(id = "b1", label = "new"))

            val vm = AdminBadgesViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            vm.update("b1", UpdateBadgeInput(label = "new"))
            advanceUntilIdle()

            val updated =
                vm.state.value.badges
                    .first { it.id == "b1" }
            assertEquals("new", updated.label)
            assertEquals(
                "b1",
                repo.updateCalls
                    .single()
                    .first,
            )
        }

    @Test
    fun deleteRemovesBadge() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueList(listOf(sampleBadge(id = "b1"), sampleBadge(id = "b2", key = "vip")))
            repo.enqueueDelete()

            val vm = AdminBadgesViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            vm.delete("b1")
            advanceUntilIdle()

            assertEquals(
                listOf("b2"),
                vm.state.value.badges
                    .map { it.id },
            )
        }

    @Test
    fun errorMessageOnFailedInitialLoad() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeAdminRepository()
            repo.enqueueListError(RuntimeException("boom"))

            val vm = AdminBadgesViewModel(repository = repo, scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.loading)
            assertNotNull(state.error)
        }
}
