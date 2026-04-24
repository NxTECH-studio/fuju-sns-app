package dev.fuju.feature.auth.domain

import dev.fuju.core.domain.SocialProvider

/**
 * Custom URL Scheme のディープリンクから OAuth コールバック情報を取り出すパーサ。
 *
 * 期待する URL 形式:
 *   `fuju://auth/callback/{provider}?state=...&code=...`
 *
 * React 版 `../auth-component/src/store/AuthStore.ts` の
 * `socialRedirectURI(provider)` と同じ構造（provider を path segment、
 * state / code を query に持つ）をモバイル向けに踏襲する。
 */
data class OAuthCallback(
    val provider: SocialProvider,
    val state: String,
    val code: String,
)

object OAuthCallbackParser {
    private const val SCHEME = "fuju"
    private const val HOST = "auth"
    private const val PATH_PREFIX = "/callback/"

    /**
     * URL 文字列を解釈し、期待する形式なら [OAuthCallback] を返す。
     * 形式が違う、provider が未知、`state` または `code` が欠けている場合は null。
     *
     * プラットフォーム標準の URL パーサを使わず文字列で処理することで commonMain に置ける。
     * 入力は OS から受け取る信頼できる URL 文字列を想定（悪意ある文字列に対する完全な
     * 堅牢性は呼び出し側で state / code 値を AuthCore で検証することに委ねる）。
     */
    fun parse(url: String): OAuthCallback? {
        val schemeSep = url.indexOf("://")
        if (schemeSep < 0) return null
        if (!url.regionMatches(0, SCHEME, 0, SCHEME.length, ignoreCase = true)) return null
        if (schemeSep != SCHEME.length) return null

        val afterScheme = url.substring(schemeSep + 3)
        val pathStart = afterScheme.indexOf('/')
        val host = if (pathStart < 0) afterScheme else afterScheme.substring(0, pathStart)
        if (!host.equals(HOST, ignoreCase = true)) return null
        if (pathStart < 0) return null

        val pathAndQuery = afterScheme.substring(pathStart)
        val queryStart = pathAndQuery.indexOf('?')
        val path = if (queryStart < 0) pathAndQuery else pathAndQuery.substring(0, queryStart)
        val query = if (queryStart < 0) "" else pathAndQuery.substring(queryStart + 1)

        if (!path.startsWith(PATH_PREFIX)) return null
        val providerSlug = path.substring(PATH_PREFIX.length).trimEnd('/')
        if (providerSlug.isEmpty()) return null
        val provider = SocialProvider.fromSlug(providerSlug) ?: return null

        val params = parseQuery(query)
        val state = params["state"] ?: return null
        val code = params["code"] ?: return null
        if (state.isEmpty() || code.isEmpty()) return null

        return OAuthCallback(provider, state, code)
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query
            .split('&')
            .mapNotNull { pair ->
                if (pair.isEmpty()) return@mapNotNull null
                val eq = pair.indexOf('=')
                if (eq < 0) {
                    percentDecode(pair) to ""
                } else {
                    percentDecode(pair.substring(0, eq)) to percentDecode(pair.substring(eq + 1))
                }
            }.toMap()
    }

    /**
     * `+` → space、`%XX` → byte として accumulate し、最後に UTF-8 として `String` に復号する。
     * マルチバイト文字（例: `%E3%81%82` = "あ"）を壊さず正しく戻せる。
     * `%` の後が 16 進 2 桁でない場合はその `%` をそのまま出力（寛容側）。
     */
    private fun percentDecode(value: String): String {
        if ('%' !in value && '+' !in value) return value
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '+' -> {
                    bytes.add(' '.code.toByte())
                    i++
                }
                c == '%' && i + 2 <= value.length - 1 -> {
                    val hi = value[i + 1].digitToIntOrNull(16)
                    val lo = value[i + 2].digitToIntOrNull(16)
                    if (hi != null && lo != null) {
                        bytes.add(((hi shl 4) or lo).toByte())
                        i += 3
                    } else {
                        bytes.add(c.code.toByte())
                        i++
                    }
                }
                else -> {
                    // ASCII のみ想定。多バイト char は percent-encoding 経由で来るため、
                    // ここに来る char は ASCII 範囲に収まる前提。はみ出た char はそのまま byte 化。
                    bytes.add(c.code.toByte())
                    i++
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}
