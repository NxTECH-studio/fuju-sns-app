package dev.fuju.feature.auth.data

import dev.fuju.core.network.FujuHttpClientFactory
import dev.fuju.core.storage.InMemorySessionHintStore
import dev.fuju.core.storage.InMemoryTokenStorage
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
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthRepositoryTest {
    private fun mockClient(handler: MockRequestHandler): HttpClient {
        val engine = MockEngine { req -> handler(req) }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(FujuHttpClientFactory.jsonFormat)
            }
            install(DefaultRequest) {
                url("http://mock")
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
    }

    @Test
    fun loginReturnsAuthenticatedResponse() =
        runTest {
            val client =
                mockClient { req ->
                    assertEquals("/v1/auth/login", req.url.encodedPath)
                    respond(
                        content = """{"access_token":"at","expires_in":900,"mfa_required":false}""".toByteArray(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val repo = AuthRepository(client, InMemoryTokenStorage(), InMemorySessionHintStore())
            val res = repo.login("alice", "hunter42")
            assertEquals(false, res.mfaRequired)
            assertEquals("at", res.accessToken)
            assertEquals(900L, res.expiresInSec)
        }

    @Test
    fun loginMFARequiredCarriesPreToken() =
        runTest {
            val client =
                mockClient { _ ->
                    respond(
                        content = """{"mfa_required":true,"pre_token":"pt"}""".toByteArray(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val repo = AuthRepository(client, InMemoryTokenStorage(), InMemorySessionHintStore())
            val res = repo.login("alice", "pw")
            assertEquals(true, res.mfaRequired)
            assertEquals("pt", res.preToken)
        }

    @Test
    fun profileMapsDtoToDomain() =
        runTest {
            val client =
                mockClient { _ ->
                    respond(
                        content =
                            """
                            {
                              "id":"u1","email":"a@b","public_id":"alice",
                              "display_name":"Alice","icon_url":null,
                              "mfa_enabled":false,"mfa_verified":false,
                              "linked_providers":["google"],
                              "created_at":"2026-04-01T00:00:00Z"
                            }
                            """.trimIndent().toByteArray(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val repo = AuthRepository(client, InMemoryTokenStorage(), InMemorySessionHintStore())
            val user = repo.loadProfile()
            assertEquals("alice", user.publicId)
            assertEquals("Alice", user.displayName)
            assertEquals(1, user.linkedProviders.size)
        }
}
