package dev.fuju.feature.auth.domain

import dev.fuju.core.domain.SocialProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OAuthCallbackParserTest {
    @Test
    fun parsesGoogleCallback() {
        val result = OAuthCallbackParser.parse("fuju://auth/callback/google?state=abc123&code=def456")
        assertEquals(SocialProvider.GOOGLE, result?.provider)
        assertEquals("abc123", result?.state)
        assertEquals("def456", result?.code)
    }

    @Test
    fun parsesTwitchAndX() {
        val t = OAuthCallbackParser.parse("fuju://auth/callback/twitch?state=s&code=c")
        assertEquals(SocialProvider.TWITCH, t?.provider)
        val x = OAuthCallbackParser.parse("fuju://auth/callback/x?state=s&code=c")
        assertEquals(SocialProvider.X, x?.provider)
    }

    @Test
    fun percentDecodesStateAndCode() {
        // state=hello world!, code=a+b (ASCII 43 that URL encodes as %2B)
        val result =
            OAuthCallbackParser.parse(
                "fuju://auth/callback/google?state=hello%20world%21&code=a%2Bb",
            )
        assertEquals("hello world!", result?.state)
        assertEquals("a+b", result?.code)
    }

    @Test
    fun plusInQueryIsTreatedAsSpace() {
        val result = OAuthCallbackParser.parse("fuju://auth/callback/google?state=he+llo&code=c")
        assertEquals("he llo", result?.state)
    }

    @Test
    fun trailingSlashAfterProviderIsAccepted() {
        val result = OAuthCallbackParser.parse("fuju://auth/callback/google/?state=s&code=c")
        assertEquals(SocialProvider.GOOGLE, result?.provider)
    }

    @Test
    fun rejectsWrongScheme() {
        assertNull(OAuthCallbackParser.parse("https://auth/callback/google?state=s&code=c"))
        assertNull(OAuthCallbackParser.parse("app://auth/callback/google?state=s&code=c"))
    }

    @Test
    fun rejectsWrongHost() {
        assertNull(OAuthCallbackParser.parse("fuju://other/callback/google?state=s&code=c"))
    }

    @Test
    fun rejectsWrongPath() {
        assertNull(OAuthCallbackParser.parse("fuju://auth/login/google?state=s&code=c"))
        assertNull(OAuthCallbackParser.parse("fuju://auth/callback?state=s&code=c"))
    }

    @Test
    fun rejectsUnknownProvider() {
        assertNull(OAuthCallbackParser.parse("fuju://auth/callback/facebook?state=s&code=c"))
    }

    @Test
    fun rejectsMissingStateOrCode() {
        assertNull(OAuthCallbackParser.parse("fuju://auth/callback/google?code=c"))
        assertNull(OAuthCallbackParser.parse("fuju://auth/callback/google?state=s"))
        assertNull(OAuthCallbackParser.parse("fuju://auth/callback/google"))
    }

    @Test
    fun rejectsEmptyStateOrCode() {
        assertNull(OAuthCallbackParser.parse("fuju://auth/callback/google?state=&code=c"))
        assertNull(OAuthCallbackParser.parse("fuju://auth/callback/google?state=s&code="))
    }

    @Test
    fun schemeIsCaseInsensitive() {
        val result = OAuthCallbackParser.parse("FUJU://auth/callback/google?state=s&code=c")
        assertEquals(SocialProvider.GOOGLE, result?.provider)
    }
}
