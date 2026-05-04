package dev.fuju.core.telemetry

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelemetryHttpClientTest {
    private fun mockClient(handler: MockRequestHandler): HttpClient {
        val engine = MockEngine { req -> handler(req) }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(DefaultRequest) {
                url("http://mock")
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
    }

    private val sampleEvent =
        TelemetryEvent(
            itemId = "post-1",
            eventType = FrontendEventType.VIEW_END,
            timestamp = "2026-05-03T10:00:00Z",
            durationSeconds = 4.5,
            positionSeconds = 4.5,
        )

    @Test
    fun postsToTenantPath() =
        runTest {
            var capturedPath: String? = null
            val client =
                mockClient { req ->
                    capturedPath = req.url.encodedPath
                    respond(
                        content = """{"accepted":1}""".toByteArray(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val sender =
                TelemetryHttpClient(
                    client = client,
                    tenantId = "sns_a",
                    signedInProvider = { true },
                )
            sender.sendBatch(listOf(sampleEvent))
            assertEquals("/v1/sns_a/events", capturedPath)
        }

    @Test
    fun emptyBatchSkipsHttpCall() =
        runTest {
            var called = false
            val client =
                mockClient { _ ->
                    called = true
                    respond("", HttpStatusCode.OK)
                }
            val sender =
                TelemetryHttpClient(
                    client = client,
                    tenantId = "sns_a",
                    signedInProvider = { true },
                )
            sender.sendBatch(emptyList())
            assertEquals(false, called)
        }

    @Test
    fun signedOutDropsBatch() =
        runTest {
            var called = false
            val client =
                mockClient { _ ->
                    called = true
                    respond("", HttpStatusCode.OK)
                }
            val sender =
                TelemetryHttpClient(
                    client = client,
                    tenantId = "sns_a",
                    signedInProvider = { false },
                )
            sender.sendBatch(listOf(sampleEvent))
            assertEquals(false, called)
        }

    @Test
    fun wireShapeOmitsUserIdAndUsesSnakeCaseFields() =
        runTest {
            // Round-trip: encode a TelemetryEventWire the way the client would,
            // then decode and assert the wire shape. Critically: the JSON
            // must NOT carry user_id — the model derives it from the
            // AuthCore Bearer's sub claim server-side.
            val wire =
                TelemetryEventWire(
                    itemId = sampleEvent.itemId,
                    eventType = sampleEvent.eventType,
                    timestamp = sampleEvent.timestamp,
                    durationSeconds = sampleEvent.durationSeconds,
                    positionSeconds = sampleEvent.positionSeconds,
                )
            val batch = TelemetryBatch(events = listOf(wire))
            val json = Json.encodeToString(TelemetryBatch.serializer(), batch)
            val parsed = Json.parseToJsonElement(json).jsonObject
            val ev = parsed["events"]!!.jsonArray[0].jsonObject
            assertTrue(
                "user_id" !in ev,
                "wire payload must not contain user_id; got $ev",
            )
            assertEquals("post-1", ev["item_id"]!!.jsonPrimitive.content)
            assertEquals("view_end", ev["event_type"]!!.jsonPrimitive.content)
            assertEquals(4.5, ev["duration_seconds"]!!.jsonPrimitive.content.toDouble())
            assertNull(ev["metadata"])
        }
}
