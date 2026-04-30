@file:OptIn(ExperimentalTime::class)

package dev.fuju.feature.timeline.ui

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import dev.fuju.core.telemetry.FrontendEventType
import dev.fuju.core.telemetry.TelemetryDispatcher
import dev.fuju.core.telemetry.TelemetryEvent
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Compose impression tracker. Mirrors the React frontend's
 * `useImpressionTracker` hook but uses `LazyListState.layoutInfo`
 * (which Compose maintains anyway for scroll restoration / paging
 * triggers) instead of an IntersectionObserver.
 *
 * Thresholds (RFC-LT-003 / docs/sns_log_contract.md §5):
 *
 *   - view_start fires when ≥VIEW_THRESHOLD_RATIO of the item is in
 *     the viewport for ≥VIEW_DWELL_MS. Re-entry after view_end
 *     starts a fresh cycle.
 *   - view_end fires when an item drops below the threshold AFTER a
 *     view_start. duration_seconds is the wall-clock interval between
 *     view_start and view_end.
 *   - scroll_stop fires for the in-between band (25–50%) when held
 *     for ≥SCROLL_STOP_DWELL_MS. Won't fire if a view_start already
 *     did for this mount.
 *
 * Lifecycle:
 *   - The hook is a Composable; bind it inside [TimelineScreen] /
 *     [HomeTimelineScreen] etc., passing the [LazyListState] used by
 *     the LazyColumn and a function that resolves index → post id.
 *   - When the calling composable leaves composition, the LaunchedEffect
 *     is cancelled and the per-id state map is dropped. view_end is
 *     fired synchronously for any items that were in the `viewing`
 *     phase.
 */
@Composable
fun ImpressionTracker(
    listState: LazyListState,
    dispatcher: TelemetryDispatcher,
    itemKeyAt: (index: Int) -> String?,
) {
    LaunchedEffect(listState, dispatcher) {
        val state = mutableMapOf<String, ItemPhase>()
        try {
            // snapshotFlow re-emits whenever any LazyListState
            // observable read inside the lambda changes — we read the
            // whole LayoutInfo so any visible-item / offset shift
            // triggers re-evaluation. Compose dedups same-value
            // emissions internally so resting frames are cheap.
            snapshotFlow { listState.layoutInfo }
                .collectLatest { info ->
                    val visibility = computeVisibility(info, itemKeyAt)
                    // Poll until either the next emission cancels us
                    // (collectLatest semantics) or every per-item
                    // phase settles (no pending dwell timer left).
                    // Required because a static viewport — user holds
                    // still — produces no further layoutInfo updates,
                    // so a one-shot transition() call would never
                    // advance VIEW_PENDING → VIEWING.
                    while (true) {
                        val now = Clock.System.now()
                        transition(state, visibility, now, dispatcher)
                        if (state.values.none { it.pendingTimerKind != null }) break
                        delay(IDLE_POLL_MS)
                    }
                }
        } finally {
            // Composition tear-down: synthesise view_end for anyone
            // mid-view so we don't leak counts.
            val now = Clock.System.now()
            for ((id, phase) in state) {
                if (phase.kind == PhaseKind.VIEWING && phase.viewStartedAt != null) {
                    dispatcher.enqueue(viewEnd(id, now, phase))
                }
            }
        }
    }
}

private const val VIEW_THRESHOLD_RATIO = 0.5
private const val VIEW_DWELL_MS = 1_000L
private const val SCROLL_STOP_THRESHOLD_RATIO = 0.25
private const val SCROLL_STOP_DWELL_MS = 500L
private const val IDLE_POLL_MS = 100L

private enum class PhaseKind { IDLE, SCROLL_STOP_PENDING, VIEW_PENDING, VIEWING }

private enum class TimerKind { SCROLL_STOP, VIEW_START }

private data class ItemPhase(
    val kind: PhaseKind = PhaseKind.IDLE,
    /** Wall-clock when phase entered (ms epoch). */
    val phaseStartedAtMs: Long = 0L,
    /** Set when in VIEWING — wall-clock at view_start emission. */
    val viewStartedAt: Instant? = null,
    /** Which dwell timer is being run-in. */
    val pendingTimerKind: TimerKind? = null,
    /** True after scroll_stop has fired this mount; suppresses re-fire. */
    val scrollStopFired: Boolean = false,
)

/** Map of item-id → visibility ratio for the current frame. */
private fun computeVisibility(
    info: LazyListLayoutInfo,
    itemKeyAt: (Int) -> String?,
): Map<String, Double> {
    val viewportStart = info.viewportStartOffset
    val viewportEnd = info.viewportEndOffset
    val out = mutableMapOf<String, Double>()
    for (item in info.visibleItemsInfo) {
        val itemSize = item.size
        if (itemSize <= 0) continue
        val visibleStart = maxOf(item.offset, viewportStart)
        val visibleEnd = minOf(item.offset + item.size, viewportEnd)
        val visiblePx = (visibleEnd - visibleStart).coerceAtLeast(0)
        val ratio = visiblePx.toDouble() / itemSize.toDouble()
        val key = itemKeyAt(item.index) ?: continue
        out[key] = ratio
    }
    return out
}

private fun transition(
    state: MutableMap<String, ItemPhase>,
    visibility: Map<String, Double>,
    now: Instant,
    dispatcher: TelemetryDispatcher,
) {
    val nowMs = now.toEpochMilliseconds()

    // Items that fell out of the viewport entirely → fire view_end
    // (if mid-view) and drop the entry from state.
    val gone = state.keys - visibility.keys
    for (id in gone) {
        val phase = state[id] ?: continue
        if (phase.kind == PhaseKind.VIEWING && phase.viewStartedAt != null) {
            dispatcher.enqueue(viewEnd(id, now, phase))
        }
        state.remove(id)
    }

    for ((id, ratio) in visibility) {
        val prev = state[id] ?: ItemPhase()
        val next = nextPhase(prev, ratio, nowMs)

        if (next.kind == PhaseKind.VIEWING && prev.kind != PhaseKind.VIEWING) {
            dispatcher.enqueue(
                TelemetryEvent(
                    itemId = id,
                    eventType = FrontendEventType.VIEW_START,
                    timestamp = now.toString(),
                ),
            )
            state[id] = next.copy(viewStartedAt = now)
            continue
        }
        if (prev.kind == PhaseKind.VIEWING && next.kind != PhaseKind.VIEWING) {
            dispatcher.enqueue(viewEnd(id, now, prev))
            state[id] = next.copy(viewStartedAt = null)
            continue
        }
        if (prev.kind == PhaseKind.SCROLL_STOP_PENDING &&
            next.kind == PhaseKind.IDLE &&
            next.scrollStopFired &&
            !prev.scrollStopFired
        ) {
            dispatcher.enqueue(
                TelemetryEvent(
                    itemId = id,
                    eventType = FrontendEventType.SCROLL_STOP,
                    timestamp = now.toString(),
                ),
            )
        }
        state[id] = next
    }
}

/**
 * Pure state-machine step. Decides what phase the item belongs in
 * given the new visibility ratio and elapsed time since the previous
 * phase entry. Side effects (dispatcher.enqueue) are handled by the
 * caller after diffing prev/next.
 */
private fun nextPhase(prev: ItemPhase, ratio: Double, nowMs: Long): ItemPhase {
    val elapsed = nowMs - prev.phaseStartedAtMs

    return when {
        ratio >= VIEW_THRESHOLD_RATIO -> {
            when (prev.kind) {
                PhaseKind.VIEWING -> prev
                PhaseKind.VIEW_PENDING ->
                    if (elapsed >= VIEW_DWELL_MS) {
                        prev.copy(
                            kind = PhaseKind.VIEWING,
                            phaseStartedAtMs = nowMs,
                            pendingTimerKind = null,
                        )
                    } else {
                        prev
                    }
                else ->
                    ItemPhase(
                        kind = PhaseKind.VIEW_PENDING,
                        phaseStartedAtMs = nowMs,
                        pendingTimerKind = TimerKind.VIEW_START,
                        scrollStopFired = prev.scrollStopFired,
                    )
            }
        }
        ratio >= SCROLL_STOP_THRESHOLD_RATIO -> {
            // In-between band. If we were viewing, drop out (caller
            // will emit view_end). Else schedule scroll_stop.
            when (prev.kind) {
                PhaseKind.VIEWING -> ItemPhase(
                    kind = PhaseKind.IDLE,
                    phaseStartedAtMs = nowMs,
                    scrollStopFired = prev.scrollStopFired,
                )
                PhaseKind.SCROLL_STOP_PENDING ->
                    if (!prev.scrollStopFired && elapsed >= SCROLL_STOP_DWELL_MS) {
                        ItemPhase(
                            kind = PhaseKind.IDLE,
                            phaseStartedAtMs = nowMs,
                            scrollStopFired = true,
                        )
                    } else {
                        prev
                    }
                else ->
                    if (prev.scrollStopFired) {
                        prev.copy(kind = PhaseKind.IDLE, pendingTimerKind = null)
                    } else {
                        ItemPhase(
                            kind = PhaseKind.SCROLL_STOP_PENDING,
                            phaseStartedAtMs = nowMs,
                            pendingTimerKind = TimerKind.SCROLL_STOP,
                            scrollStopFired = false,
                        )
                    }
            }
        }
        else -> {
            // Below SCROLL_STOP threshold — call it idle / off-screen.
            ItemPhase(
                kind = PhaseKind.IDLE,
                phaseStartedAtMs = nowMs,
                scrollStopFired = prev.scrollStopFired,
            )
        }
    }
}

private fun viewEnd(
    id: String,
    now: Instant,
    phase: ItemPhase,
): TelemetryEvent {
    val started = phase.viewStartedAt ?: now
    val durSec = (now.toEpochMilliseconds() - started.toEpochMilliseconds()).toDouble() / 1000.0
    return TelemetryEvent(
        itemId = id,
        eventType = FrontendEventType.VIEW_END,
        timestamp = now.toString(),
        durationSeconds = durSec,
        positionSeconds = durSec,
    )
}
