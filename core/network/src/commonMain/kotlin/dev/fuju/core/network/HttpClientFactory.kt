package dev.fuju.core.network

import dev.fuju.core.storage.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * KMP 共通の Ktor HttpClient 生成エントリ。
 *
 * - `ContentNegotiation(Json)` — kotlinx.serialization で JSON を双方向変換
 * - `HttpCookies(AcceptAllCookiesStorage)` — AuthCore の `refresh_token` HttpOnly Cookie を自動管理
 * - `DefaultRequest` — baseURL は呼び出し側で各 repository が指定するため、ここでは共通ヘッダのみ
 * - `Logging` — dev ビルドで body も含めてログ（prod は削減）
 * - `HttpTimeout` — 全体 15s、接続 5s、ソケット 10s
 * - Bearer token は [TokenStorage] を参照して都度 header を付与（後段のプラグインを使う余地あり）
 *
 * プラットフォーム依存の engine は `actual fun provideEngineFactory(): HttpClientEngineFactory<*>` で
 * 各 target が返す。Android は OkHttp、iOS は Darwin。
 */
class FujuHttpClientFactory(
    private val tokenStorage: TokenStorage,
    private val engineFactory: HttpClientEngineFactory<*>,
    private val config: FujuNetworkConfig,
) {
    /**
     * [baseURL] をデフォルト URL として持つ HttpClient を作る。
     * AuthCore 用 / Backend 用で別インスタンスを作ることを想定。
     */
    fun create(baseURL: String, enableBearer: Boolean): HttpClient {
        val normalizedBase = baseURL.trimEnd('/')
        return HttpClient(engineFactory) {
            installCommon(normalizedBase, enableBearer)
        }
    }

    private fun HttpClientConfig<*>.installCommon(baseURL: String, enableBearer: Boolean) {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutMs
            connectTimeoutMillis = config.connectTimeoutMs
            socketTimeoutMillis = config.socketTimeoutMs
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = if (config.verboseLogging) LogLevel.BODY else LogLevel.INFO
        }
        install(DefaultRequest) {
            url(baseURL)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("X-Fuju-Client", "fuju-kmp/0.1")
        }
        if (enableBearer) {
            install(BearerTokenPlugin) {
                getToken = { tokenStorage.currentToken()?.accessToken }
            }
        }
    }

    companion object {
        /** プロジェクト共通の JSON 構成。snake_case は serializer 側で個別マッピング。 */
        val jsonFormat: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
            prettyPrint = false
        }
    }
}

/**
 * HttpClient に注入する設定値。BuildConfig / xcconfig から流し込む。
 */
data class FujuNetworkConfig(
    val requestTimeoutMs: Long = 15_000L,
    val connectTimeoutMs: Long = 5_000L,
    val socketTimeoutMs: Long = 10_000L,
    val verboseLogging: Boolean = false,
)
