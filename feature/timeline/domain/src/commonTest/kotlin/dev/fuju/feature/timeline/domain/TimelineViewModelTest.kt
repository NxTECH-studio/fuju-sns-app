package dev.fuju.feature.timeline.domain

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {
    @Test
    fun initialLoadFetchesGlobalAndEmitsItems() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeTimelineRepository()
            val page =
                PostPage(
                    items = listOf(samplePost("p1"), samplePost("p2")),
                    nextCursor = "cursor-2",
                )
            repo.enqueueGlobal(page)

            val vm = TimelineViewModel(repository = repo, kind = TimelineKind.Global, scope = this)

            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(listOf("p1", "p2"), state.items.map { it.id })
            assertEquals("cursor-2", state.nextCursor)
            assertFalse(state.loading)
            assertNull(state.error)
            assertEquals(1, repo.globalCalls.size)
            assertNull(repo.globalCalls.first().cursor)
        }

    @Test
    fun loadMoreAppendsNextPageAndCarriesCursor() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeTimelineRepository()
            repo.enqueueGlobal(PostPage(items = listOf(samplePost("p1")), nextCursor = "cursor-2"))
            repo.enqueueGlobal(PostPage(items = listOf(samplePost("p2"), samplePost("p3")), nextCursor = null))

            val vm = TimelineViewModel(repository = repo, kind = TimelineKind.Global, scope = this)
            advanceUntilIdle()

            vm.loadMore()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(listOf("p1", "p2", "p3"), state.items.map { it.id })
            assertNull(state.nextCursor)
            assertFalse(state.canLoadMore)
            assertEquals(2, repo.globalCalls.size)
            assertEquals("cursor-2", repo.globalCalls[1].cursor)
        }

    @Test
    fun loadMoreSkippedWhenNoCursor() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeTimelineRepository()
            repo.enqueueGlobal(PostPage(items = listOf(samplePost("only")), nextCursor = null))

            val vm = TimelineViewModel(repository = repo, kind = TimelineKind.Global, scope = this)
            advanceUntilIdle()

            vm.loadMore()
            advanceUntilIdle()

            // 追加の API 呼び出しが起きない
            assertEquals(1, repo.globalCalls.size)
        }

    @Test
    fun refreshResetsCursorAndRefetches() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeTimelineRepository()
            repo.enqueueGlobal(PostPage(items = listOf(samplePost("old-1")), nextCursor = "c-old"))
            repo.enqueueGlobal(PostPage(items = listOf(samplePost("new-1"), samplePost("new-2")), nextCursor = null))

            val vm = TimelineViewModel(repository = repo, kind = TimelineKind.Global, scope = this)
            advanceUntilIdle()

            vm.refresh()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(listOf("new-1", "new-2"), state.items.map { it.id })
            assertNull(state.nextCursor)
            assertEquals(2, repo.globalCalls.size)
            assertNull(repo.globalCalls[1].cursor)
        }

    @Test
    fun toggleLikeOptimisticUpdateReflectsImmediately() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeTimelineRepository()
            val gate = CompletableDeferred<Unit>()
            repo.likeGate = gate
            repo.enqueueGlobal(
                PostPage(
                    items = listOf(samplePost("p1", likedByViewer = false, likesCount = 3)),
                    nextCursor = null,
                ),
            )

            val vm = TimelineViewModel(repository = repo, kind = TimelineKind.Global, scope = this)
            advanceUntilIdle()

            vm.state.test {
                // 初期ロード済みの状態を読む
                val initial = awaitItem()
                assertEquals(3, initial.items.single().likesCount)
                assertFalse(initial.items.single().likedByViewer)

                vm.toggleLike(initial.items.single())
                val afterOptimistic = awaitItem()
                assertEquals(4, afterOptimistic.items.single().likesCount)
                assertTrue(afterOptimistic.items.single().likedByViewer)

                // gate を開放すると API 呼び出しが完了するが、成功時は state 変化なし
                gate.complete(Unit)
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("p1"), repo.likeCalls)
        }

    @Test
    fun toggleLikeRollbackOnFailure() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeTimelineRepository()
            repo.enqueueGlobal(
                PostPage(
                    items = listOf(samplePost("p1", likedByViewer = true, likesCount = 5)),
                    nextCursor = null,
                ),
            )
            repo.nextUnlikeError = RuntimeException("boom")

            val vm = TimelineViewModel(repository = repo, kind = TimelineKind.Global, scope = this)
            advanceUntilIdle()

            val before =
                vm.state.value.items
                    .single()
            vm.toggleLike(before)
            advanceUntilIdle()

            val after =
                vm.state.value.items
                    .single()
            // Optimistic 更新後に rollback されているので元に戻っている
            assertTrue(after.likedByViewer)
            assertEquals(5, after.likesCount)
            // sanitizeError() で AuthException 以外は固定文言に丸められる。
            assertEquals("エラーが発生しました。", vm.state.value.error)
        }

    @Test
    fun createPostPrependsToItems() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeTimelineRepository()
            repo.enqueueGlobal(PostPage(items = listOf(samplePost("old")), nextCursor = null))
            val vm = TimelineViewModel(repository = repo, kind = TimelineKind.Global, scope = this)
            advanceUntilIdle()

            vm.createPost(content = "hi")
            advanceUntilIdle()

            val ids =
                vm.state.value.items
                    .map { it.id }
            assertEquals("new-0", ids.first())
            assertEquals(listOf("new-0", "old"), ids)
            assertEquals(1, repo.createCalls.size)
        }

    @Test
    fun createReplyDoesNotPrependToTimeline() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeTimelineRepository()
            repo.enqueueGlobal(PostPage(items = listOf(samplePost("root")), nextCursor = null))
            val vm = TimelineViewModel(repository = repo, kind = TimelineKind.Global, scope = this)
            advanceUntilIdle()

            vm.createPost(content = "reply", parentPostId = "root")
            advanceUntilIdle()

            assertEquals(
                listOf("root"),
                vm.state.value.items
                    .map { it.id },
            )
            assertEquals("root", repo.createCalls.single().parentPostId)
        }

    @Test
    fun errorMessageOnFailedInitialLoad() =
        runTest(StandardTestDispatcher()) {
            val repo = FakeTimelineRepository()
            repo.enqueueGlobalError(RuntimeException("network down"))

            val vm = TimelineViewModel(repository = repo, kind = TimelineKind.Global, scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            // sanitizeError() で RuntimeException は固定文言に丸められる。
            assertEquals("エラーが発生しました。", state.error)
            assertFalse(state.loading)
            assertTrue(state.items.isEmpty())
        }
}
