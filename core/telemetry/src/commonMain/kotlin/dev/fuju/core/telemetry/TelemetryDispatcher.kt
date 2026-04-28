package dev.fuju.core.telemetry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * In-process bounded queue + flusher. Mirrors the SNS-backend's
 * fuju.Dispatcher and the React frontend's TelemetryBatcher. Hot path
 * callers (the Compose impression-tracker) only [enqueue]; the
 * background coroutine drains the queue and POSTs in batches.
 *
 * Loss policy: when the queue is full an enqueue drops the oldest
 * entry. Telemetry is best-effort — sustained outages do not get
 * retried because the daily fuju batch on the SNS-backend side
 * absorbs short-term gaps.
 *
 * Lifecycle:
 *   - Construction starts the coroutine (launch under [scope]).
 *   - [shutdown] cancels the loop and runs one final best-effort
 *     flush. Idempotent.
 */
class TelemetryDispatcher private constructor(
    private val sender: TelemetrySender,
    private val scope: CoroutineScope,
    private val batchSize: Int,
    private val flushIntervalMs: Long,
    private val queueCapacity: Int,
) {
    constructor(
        sender: TelemetrySender,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS,
        queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    ) : this(
        sender = sender,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        batchSize = batchSize,
        flushIntervalMs = flushIntervalMs,
        queueCapacity = queueCapacity,
    )

    private val channel = Channel<TelemetryEvent>(capacity = queueCapacity)
    private val mutex = Mutex()
    private val pending = ArrayDeque<TelemetryEvent>(initialCapacity = batchSize)
    private var loopJob: Job? = null
    private var stopped = false

    init {
        loopJob = scope.launch { loop() }
    }

    fun enqueue(event: TelemetryEvent) {
        if (stopped) return
        // Channel.trySend is non-blocking. On overflow we discard the
        // oldest entry by pulling one out of the channel and pushing
        // again. There's a race with the consumer here — under heavy
        // contention the consumer may have already drained the slot —
        // which is fine; the next enqueue will succeed cleanly.
        if (!channel.trySend(event).isSuccess) {
            channel.tryReceive() // drop oldest
            channel.trySend(event)
        }
    }

    suspend fun flush() {
        mutex.withLock {
            // Drain whatever the loop hasn't picked up yet.
            while (true) {
                val r = channel.tryReceive()
                if (r.isClosed || r.isFailure) break
                val ev = r.getOrNull() ?: break
                pending.addLast(ev)
            }
            if (pending.isEmpty()) return@withLock
            val batch = pending.toList()
            pending.clear()
            runCatching { sender.sendBatch(batch) }
        }
    }

    suspend fun shutdown() {
        if (stopped) return
        stopped = true
        flush()
        channel.close()
        loopJob?.cancel()
        scope.cancel()
    }

    private suspend fun loop() {
        while (scope.isActive) {
            // Block on the channel until either an event arrives or
            // the flush interval elapses. The first event after a
            // flush opens a batching window; subsequent events arrive
            // via tryReceive without blocking until either we hit
            // batchSize or the window closes.
            val first = withTimeoutOrNull(flushIntervalMs) { channel.receive() }
            if (first != null) {
                mutex.withLock { pending.addLast(first) }
                while (pending.size < batchSize) {
                    val r = channel.tryReceive()
                    if (!r.isSuccess) break
                    mutex.withLock { pending.addLast(r.getOrThrow()) }
                }
            }
            if (pending.isNotEmpty()) {
                val batch: List<TelemetryEvent>
                mutex.withLock {
                    batch = pending.toList()
                    pending.clear()
                }
                runCatching { sender.sendBatch(batch) }
            }
        }
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 32
        const val DEFAULT_FLUSH_INTERVAL_MS = 5_000L
        const val DEFAULT_QUEUE_CAPACITY = 256
    }
}
