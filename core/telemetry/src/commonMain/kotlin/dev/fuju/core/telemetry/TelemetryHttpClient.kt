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
     * POST `events` to the SNS backend's `/v1/me/events`. Returns
     * normally on 2xx, throws on 4xx/5xx (the dispatcher catches and
     * logs but does not retry — telemetry is best-effort).
     */
    suspend fun sendBatch(events: List<TelemetryEvent>)
}

/**
 * Ktor-backed implementation. The Bearer token is attached by the
 * BearerTokenPlugin already configured on [client]; this layer only
 * cares about the wire shape and status mapping.
 */
class TelemetryHttpClient(
    private val client: HttpClient,
) : TelemetrySender {
    override suspend fun sendBatch(events: List<TelemetryEvent>) {
        if (events.isEmpty()) return
        val response =
            client.post("/v1/me/events") {
                contentType(ContentType.Application.Json)
                setBody(TelemetryBatch(events = events))
            }
        response.throwIfErrorOrDiscard()
    }
}
