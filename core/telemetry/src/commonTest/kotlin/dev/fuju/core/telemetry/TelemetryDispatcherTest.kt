package dev.fuju.core.telemetry

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RecordingSender : TelemetrySender {
    val batches = mutableListOf<List<TelemetryEvent>>()
    var failNext: Boolean = false

    override suspend fun sendBatch(events: List<TelemetryEvent>) {
        if (failNext) {
            failNext = false
            error("simulated upstream failure")
        }
        batches += events.toList()
    }
}

private fun newEvent(itemId: String): TelemetryEvent =
    TelemetryEvent(
        itemId = itemId,
        eventType = FrontendEventType.VIEW_START,
        timestamp = "2023-11-14T22:13:20Z",
    )

class TelemetryDispatcherTest {
    /**
     * Internal scope-accepting ctor is the only way to bind the
     * dispatcher's loop coroutine to runTest's TestScope so virtual
     * time advances through `delay` / `advanceUntilIdle` correctly.
     * Production code uses the public ctor which spawns its own
     * Dispatchers.Default scope (no test rig dependency).
     */
    @Test
    fun batchSizeFlush() =
        runTest {
            val sender = RecordingSender()
            val d =
                TelemetryDispatcher(
                    sender = sender,
                    scope = backgroundScope,
                    batchSize = 3,
                    flushIntervalMs = 60_000L, // disabled by being long
                    queueCapacity = 16,
                )
            repeat(5) { d.enqueue(newEvent("c$it")) }
            advanceUntilIdle()
            d.shutdown()
            // First three flushed by size; trailing two flushed on shutdown.
            assertEquals(5, sender.batches.flatten().size)
        }

    @Test
    fun shutdownDrainsRemaining() =
        runTest {
            val sender = RecordingSender()
            val d =
                TelemetryDispatcher(
                    sender = sender,
                    scope = backgroundScope,
                    batchSize = 100,
                    flushIntervalMs = 60_000L,
                    queueCapacity = 16,
                )
            d.enqueue(newEvent("only"))
            d.shutdown()
            assertEquals(1, sender.batches.flatten().size)
            assertEquals(
                "only",
                sender.batches
                    .flatten()
                    .first()
                    .itemId,
            )
        }

    @Test
    fun queueOverflowDropsOldestNotNewest() =
        runTest {
            val sender = RecordingSender()
            val d =
                TelemetryDispatcher(
                    sender = sender,
                    scope = backgroundScope,
                    batchSize = 1000,
                    flushIntervalMs = 60_000L,
                    queueCapacity = 4,
                )
            repeat(8) { d.enqueue(newEvent("c$it")) }
            advanceUntilIdle()
            d.shutdown()
            val ids = sender.batches.flatten().map { it.itemId }
            assertTrue(ids.size <= 4, "queue must cap at 4, got $ids")
            // Newest must be present (drop-oldest policy).
            assertTrue(ids.contains("c7"), "newest event must be retained, got $ids")
        }

    @Test
    fun senderFailureDoesNotKillTheLoop() =
        runTest {
            val sender = RecordingSender()
            sender.failNext = true
            val d =
                TelemetryDispatcher(
                    sender = sender,
                    scope = backgroundScope,
                    batchSize = 1,
                    flushIntervalMs = 60_000L,
                    queueCapacity = 16,
                )
            d.enqueue(newEvent("first"))
            advanceTimeBy(50L)
            advanceUntilIdle()
            d.enqueue(newEvent("second"))
            advanceUntilIdle()
            d.shutdown()
            // First batch failed (recorded nothing), second batch must
            // succeed despite the prior failure.
            val ids = sender.batches.flatten().map { it.itemId }
            assertTrue(ids.contains("second"), "loop survived failure: $ids")
        }
}
