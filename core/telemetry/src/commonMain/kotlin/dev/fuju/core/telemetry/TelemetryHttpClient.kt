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
 * shape.
 *
 * The wire payload no longer carries `user_id`. The model's
 * introspection middleware (see fuju-emotion-model
 * ``api/ingestion_app.post_events``) derives the caller's user id from
 * the AuthCore Bearer's ``sub`` claim server-side, so the client must
 * not send a placeholder (and a malicious client can't spoof one
 * either).
 *
 * [signedInProvider] is consulted to skip flushes when the end-user
 * is signed out / token expired mid-flush — the model would 401 those
 * requests, so dropping locally avoids needless network traffic. The
 * race window is narrow in practice: the impression-tracker only
 * mounts under authenticated TimelineScreen variants. If we widen the
 * surface to public timelines, move the gate up into the dispatcher
 * so events stay in the channel rather than being dropped here.
 */
class TelemetryHttpClient(
    private val client: HttpClient,
    private val tenantId: String,
    private val signedInProvider: () -> Boolean,
) : TelemetrySender {
    override suspend fun sendBatch(events: List<TelemetryEvent>) {
        if (events.isEmpty()) return
        if (!signedInProvider()) return
        val wire =
            events.map { e ->
                TelemetryEventWire(
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
