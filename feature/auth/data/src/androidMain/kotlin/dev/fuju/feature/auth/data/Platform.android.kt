package dev.fuju.feature.auth.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal actual fun nowSeconds(): Long = System.currentTimeMillis() / 1_000L

internal actual fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
