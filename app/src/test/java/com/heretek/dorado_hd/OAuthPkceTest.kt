package com.heretek.dorado_hd

import com.heretek.dorado_hd.cloud.OAuthPkce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the HD PKCE implementation against the RFC 7636 test vector, the
 * authorize-URL composition, and the token exchange.
 */
class OAuthPkceTest {

    @Test
    fun computeChallenge_matchesRfc7636Vector() {
        // RFC 7636 Appendix B.
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", OAuthPkce.computeChallenge(verifier))
    }

    @Test
    fun createChallenge_producesVerifierAndMatchingChallenge() {
        val challenge = OAuthPkce.createChallenge()
        assertEquals(43, challenge.verifier.length)
        assertEquals(OAuthPkce.computeChallenge(challenge.verifier), challenge.challenge)
        assertTrue(challenge.verifier.none { it == '+' || it == '/' || it == '=' })
    }

    @Test
    fun buildAuthorizeUrl_includesPkceParameters() {
        val url = OAuthPkce.buildAuthorizeUrl(
            baseUrl = "https://cloud.example/",
            clientId = "dorado-hd",
            redirectUri = "doradohd://oauth",
            scope = "openid profile dorado.api",
            codeChallenge = "CHALLENGE",
            state = "STATE1",
        )

        assertTrue(url.startsWith("https://cloud.example/connect/authorize?"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=dorado-hd"))
        assertTrue(url.contains("redirect_uri=doradohd%3A%2F%2Foauth"))
        assertTrue(url.contains("code_challenge=CHALLENGE"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("state=STATE1"))
    }

    @Test
    fun exchangeCode_parsesTokens() {
        val http = FakeCloudHttp()
        http.enqueue(200, """{"access_token":"tok","token_type":"Bearer","expires_in":3600,"refresh_token":"rt"}""")

        val tokens = OAuthPkce.exchangeCode(http, "dorado-hd", "code", "verifier", "doradohd://oauth")

        assertEquals("tok", tokens?.accessToken)
        assertEquals(3600, tokens?.expiresIn)
        assertEquals("rt", tokens?.refreshToken)
        assertEquals("connect/token", http.lastPath)
        assertEquals("authorization_code", http.lastForm?.get("grant_type"))
        assertEquals("verifier", http.lastForm?.get("code_verifier"))
    }

    @Test
    fun exchangeCode_returnsNullOnRejection() {
        val http = FakeCloudHttp()
        http.enqueue(400, "bad")
        assertNull(OAuthPkce.exchangeCode(http, "dorado-hd", "code", "verifier", "doradohd://oauth"))
    }
}
