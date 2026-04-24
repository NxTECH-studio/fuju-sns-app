package dev.fuju.core.error

import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorCodeTest {

    @Test
    fun knownWireCodeMapsBack() {
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ErrorCode.fromWireOrUnknown("INVALID_CREDENTIALS"))
        assertEquals(ErrorCode.TOTP_CODE_INVALID, ErrorCode.fromWireOrUnknown("TOTP_CODE_INVALID"))
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, ErrorCode.fromWireOrUnknown("RATE_LIMIT_EXCEEDED"))
    }

    @Test
    fun unknownWireCodeFallsBack() {
        assertEquals(ErrorCode.UNKNOWN, ErrorCode.fromWireOrUnknown(null))
        assertEquals(ErrorCode.UNKNOWN, ErrorCode.fromWireOrUnknown("SOMETHING_NEW_FROM_BACKEND"))
    }

    @Test
    fun allValuesHaveNonEmptyWire() {
        // UNKNOWN を含めて全 35 種が wire code を持つ。
        ErrorCode.entries.forEach {
            assert(it.wire.isNotEmpty()) { "${it.name} must have a wire code" }
        }
        // 34 種の移植 + UNKNOWN = 35 件
        assertEquals(35, ErrorCode.entries.size)
    }
}
