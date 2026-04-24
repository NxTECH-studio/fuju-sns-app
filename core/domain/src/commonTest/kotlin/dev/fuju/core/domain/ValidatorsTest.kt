package dev.fuju.core.domain

import dev.fuju.core.error.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ValidatorsTest {

    @Test
    fun emailAcceptsNormalAddress() {
        assertNull(Validators.validateEmail("alice@example.com"))
    }

    @Test
    fun emailRejectsMissingAt() {
        assertEquals(ErrorCode.EMAIL_INVALID, Validators.validateEmail("alice.example.com"))
    }

    @Test
    fun passwordAcceptsSixChars() {
        assertNull(Validators.validatePassword("abcdef"))
    }

    @Test
    fun passwordRejectsFiveChars() {
        assertEquals(ErrorCode.PASSWORD_TOO_SHORT, Validators.validatePassword("abcde"))
    }

    @Test
    fun publicIdAcceptsFourToSixteenAlphaNumeric() {
        assertNull(Validators.validatePublicId("alice99"))
        assertNull(Validators.validatePublicId("abcd"))
        assertNull(Validators.validatePublicId("0123456789abcdef"))
    }

    @Test
    fun publicIdReservesShortNames() {
        assertEquals(ErrorCode.PUBLIC_ID_RESERVED, Validators.validatePublicId("a"))
        assertEquals(ErrorCode.PUBLIC_ID_RESERVED, Validators.validatePublicId("ab"))
        assertEquals(ErrorCode.PUBLIC_ID_RESERVED, Validators.validatePublicId("abc"))
    }

    @Test
    fun publicIdRejectsSymbolsAndTooLong() {
        assertEquals(ErrorCode.PUBLIC_ID_FORMAT_INVALID, Validators.validatePublicId("alice_bob"))
        assertEquals(ErrorCode.PUBLIC_ID_FORMAT_INVALID, Validators.validatePublicId("0123456789abcdefg"))
    }
}
