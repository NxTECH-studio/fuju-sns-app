package dev.fuju.feature.auth.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSDate
import platform.Foundation.NSString
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.Foundation.timeIntervalSince1970

@OptIn(ExperimentalForeignApi::class)
internal actual fun nowSeconds(): Long = NSDate().timeIntervalSince1970.toLong()

@OptIn(ExperimentalForeignApi::class)
internal actual fun urlEncode(value: String): String {
    val allowed = NSCharacterSet.URLQueryAllowedCharacterSet
    return (value as NSString).stringByAddingPercentEncodingWithAllowedCharacters(allowed) ?: value
}
