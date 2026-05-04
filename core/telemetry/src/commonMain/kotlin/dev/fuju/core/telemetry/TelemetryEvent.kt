package dev.fuju.core.telemetry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Whitelist of event types this client can emit. Server-side hooks
 * (like / follow / comment / share / save / unsave) are emitted from
 * SNS backend usecases, not from the app.
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
 * Caller-facing event captured by the impression tracker.
 *
 * `timestamp` is an ISO 8601 string (`2026-04-30T10:00:00Z`). The
 * Compose impression tracker formats from `kotlin.time.Instant` via
 * `toString()`. A string wire avoids pulling in a kotlinx-serialization
 * Instant serializer (kotlinx-datetime 0.7.0 + Kotlin 2.2 don't ship
 * one for `kotlin.time.Instant`) and keeps tenant interop simple.
 */
data class TelemetryEvent(
    val itemId: String,
    val eventType: FrontendEventType,
    val timestamp: String,
    val durationSeconds: Double? = null,
    val positionSeconds: Double? = null,
    val metadata: Map<String, String>? = null,
)

/**
 * Wire shape for fuju-emotion-model's `POST /v1/{tenant}/events`. The
 * model derives `user_id` from the AuthCore Bearer's `sub` claim
 * server-side (see ``api/ingestion_app.post_events``), so the client
 * never sends one. This closes the spoofing loophole the placeholder
 * field implied — a malicious client could otherwise set any user id.
 */
@Serializable
internal data class TelemetryEventWire(
    @SerialName("item_id") val itemId: String,
    @SerialName("event_type") val eventType: FrontendEventType,
    val timestamp: String,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    @SerialName("position_seconds") val positionSeconds: Double? = null,
    val metadata: Map<String, String>? = null,
)

@Serializable
internal data class TelemetryBatch(
    val events: List<TelemetryEventWire>,
)

@Serializable
internal data class TelemetryAcceptedResponse(
    val accepted: Int,
)
