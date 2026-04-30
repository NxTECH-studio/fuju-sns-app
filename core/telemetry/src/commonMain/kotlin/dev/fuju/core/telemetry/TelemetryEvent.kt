package dev.fuju.core.telemetry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Whitelist of event types this client can emit. Server-side hooks
 * (like / follow / comment / share / save / unsave) are emitted from
 * SNS backend usecases, not from the app — the backend rejects them
 * on `/v1/me/events` to prevent spoofing.
 *
 * Mirrors `FrontendEventType` in the React frontend repo
 * (`fuju-sns-frontend/src/api/types.ts`).
 */
@Serializable
enum class FrontendEventType {
    @SerialName("view_start")
    VIEW_START,

    @SerialName("view_end")
    VIEW_END,

    @SerialName("scroll_stop")
    SCROLL_STOP,

    @SerialName("rewind")
    REWIND,
}

/**
 * Wire shape for the SNS backend's `POST /v1/me/events` endpoint. The
 * backend overrides `user_id` server-side, so the client never sends
 * one. Mirrors `MeEventInput` in the React frontend repo and
 * `RawEvent` in fuju.
 */
/**
 * Wire shape: timestamp is the ISO 8601 representation
 * (`2026-04-30T10:00:00Z`). The Compose tracker formats from
 * `kotlin.time.Instant` via `toString()`. We don't pull in a
 * kotlinx-serialization Instant serializer because kotlinx-datetime
 * 0.7.0 + Kotlin 2.2 don't ship one for `kotlin.time.Instant` at
 * present and a string wire keeps tenant interop simple.
 */
@Serializable
data class TelemetryEvent(
    @SerialName("item_id") val itemId: String,
    @SerialName("event_type") val eventType: FrontendEventType,
    val timestamp: String,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    @SerialName("position_seconds") val positionSeconds: Double? = null,
    val metadata: Map<String, String>? = null,
)

@Serializable
internal data class TelemetryBatch(
    val events: List<TelemetryEvent>,
)

@Serializable
internal data class TelemetryAcceptedResponse(
    val accepted: Int,
)
