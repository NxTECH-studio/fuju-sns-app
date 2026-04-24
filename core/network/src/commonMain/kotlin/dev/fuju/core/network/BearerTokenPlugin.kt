package dev.fuju.core.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/**
 * Bearer token を自動で `Authorization: Bearer ...` に載せる軽量プラグイン。
 *
 * Ktor 標準の `Auth` プラグインは refresh callback を含む重量版で、今回は AuthCore
 * 側の refresh が独立した手順（StateMachine の `runRefresh` が駆動）のため、
 * 読み出し専用の薄いプラグインに留める。
 */
class BearerTokenPluginConfig {
    /** null を返すと Authorization header を付けない。 */
    var getToken: () -> String? = { null }
}

val BearerTokenPlugin =
    createClientPlugin(
        name = "FujuBearerToken",
        createConfiguration = ::BearerTokenPluginConfig,
    ) {
        val tokenProvider = pluginConfig.getToken
        onRequest { request, _ ->
            val token = tokenProvider()
            if (token != null && !request.headers.contains(HttpHeaders.Authorization)) {
                request.header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }
