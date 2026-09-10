package com.heretek.dorado_hd.cloud

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Kotlin mirror of the desktop `OAuthPkceService`: RFC 7636 S256 proof-key
 * generation and the OpenIddict `/connect/authorize` / `/connect/token`
 * exchanges. No client secret is embedded.
 */
object OAuthPkce {
    data class Challenge(val verifier: String, val challenge: String)

    data class TokenSet(
        val accessToken: String,
        val tokenType: String,
        val expiresIn: Int,
        val refreshToken: String?,
    )

    fun createChallenge(random: SecureRandom = SecureRandom()): Challenge {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        val verifier = base64Url(bytes)
        return Challenge(verifier, computeChallenge(verifier))
    }

    /** BASE64URL(SHA256(ASCII(verifier))) — the S256 transformation. */
    fun computeChallenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun buildAuthorizeUrl(
        baseUrl: String,
        clientId: String,
        redirectUri: String,
        scope: String,
        codeChallenge: String,
        state: String? = null,
    ): String {
        val query = mutableListOf(
            "response_type=code",
            "client_id=${enc(clientId)}",
            "redirect_uri=${enc(redirectUri)}",
            "scope=${enc(scope)}",
            "code_challenge=${enc(codeChallenge)}",
            "code_challenge_method=S256",
        )
        if (!state.isNullOrEmpty()) {
            query.add("state=${enc(state)}")
        }
        return "${baseUrl.trimEnd('/')}/connect/authorize?${query.joinToString("&")}"
    }

    fun exchangeCode(
        http: CloudHttp,
        clientId: String,
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ): TokenSet? {
        val response = runCatching {
            http.postForm(
                "connect/token",
                mapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to clientId,
                    "code" to code,
                    "code_verifier" to codeVerifier,
                    "redirect_uri" to redirectUri,
                ),
                token = null,
            )
        }.getOrNull() ?: return null

        if (!response.isSuccess) return null
        val map = CloudJson.asObject(CloudJson.parse(response.body))
        val accessToken = CloudJson.string(map, "access_token") ?: return null
        return TokenSet(
            accessToken = accessToken,
            tokenType = CloudJson.stringOr(map, "token_type", "Bearer"),
            expiresIn = CloudJson.int(map, "expires_in"),
            refreshToken = CloudJson.string(map, "refresh_token"),
        )
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
