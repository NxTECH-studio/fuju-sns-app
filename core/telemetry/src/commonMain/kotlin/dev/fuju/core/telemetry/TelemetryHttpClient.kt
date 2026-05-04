package dev.fuju.core.telemetry

import dev.fuju.core.network.throwIfErrorOrDiscard
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Sender wraps the Ktor call so the dispatcher can stay agnostic of
 * HTTP transport in tests. Production binding is
 * [TelemetryHttpClient]; tests pass a recording stub.
 */
interface TelemetrySender {
    /**
     * POST `events` to fuju-emotion-model's `/v1/{tenant}/events`.
     * Returns normally on 2xx, throws on 4xx/5xx (the dispatcher
     * catches and logs but does not retry — telemetry is best-effort).
     */
    suspend fun sendBatch(events: List<TelemetryEvent>)
}

/**
 * Ktor-backed implementation that ingests directly into
 * fuju-emotion-model. The Bearer token is attached by the
 * BearerTokenPlugin already configured on [client]; this layer maps
 * the in-memory [TelemetryEvent] into the `/v1/{tenant}/events` wire
 * shape and stamps `user_id` from [userIdProvider] at send time.
 *
 * When [userIdProvider] returns null (signed-out / token expired
 * mid-flush) the batch is dropped. TelemetryDispatcher already drained
 * the events from its in-memory channel before calling sendBatch, so
 * there is no straightforward way to re-queue. The race window is
 * narrow in practice: the impression-tracker only mounts under
 * authenticated TimelineScreen variants, so unauthenticated enqueue is
 * rare. If we widen the surface to public timelines, move the userId
 * gate up into the dispatcher so events stay in the channel.
 */
class TelemetryHttpClient(
    private val client: HttpClient,
    private val tenantId: String,
    private val userIdProvider: () -> String?,
) : TelemetrySender {
    override suspend fun sendBatch(events: List<TelemetryEvent>) {
        if (events.isEmpty()) return
        val userId = userIdProvider() ?: return
        val wire =
            events.map { e ->
                TelemetryEventWire(
                    userId = userId,
                    itemId = e.itemId,
                    eventType = e.eventType,
                    timestamp = e.timestamp,
                    durationSeconds = e.durationSeconds,
                    positionSeconds = e.positionSeconds,
                    metadata = e.metadata,
                )
            }
        val response =
            client.post("/v1/$tenantId/events") {
                contentType(ContentType.Application.Json)
                setBody(TelemetryBatch(events = wire))
            }
        response.throwIfErrorOrDiscard()
    }
}
