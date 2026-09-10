package com.heretek.dorado_hd.cloud

import com.heretek.dorado_hd.data.repo.DoradoSettings
import java.security.SecureRandom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Interactive OAuth 2.0 Authorization Code + PKCE sign-in for the HD client.
 * The browser is launched with the authorize URL; the redirect is captured by
 * [CloudSignInCallback] and resolved here; the code is exchanged for tokens and
 * persisted in settings. The browser/await seams keep it unit-testable.
 */
class CloudSignIn(
    private val currentSettings: () -> DoradoSettings,
    private val setEnabled: suspend (Boolean) -> Unit,
    private val setToken: suspend (String) -> Unit,
    private val launchBrowser: (String) -> Unit,
    private val awaitRedirect: suspend (redirectUri: String, timeoutMs: Long) -> Map<String, String>?,
    private val httpFactory: (String) -> CloudHttp = { HttpUrlConnectionCloudHttp(it) },
    private val timeoutMs: Long = 180_000,
) {
    suspend fun signIn(): Boolean {
        val current = currentSettings()
        if (current.cloudBaseUrl.isBlank()) return false

        val pkce = OAuthPkce.createChallenge()
        val state = newState()
        val authorizeUrl = OAuthPkce.buildAuthorizeUrl(
            baseUrl = current.cloudBaseUrl,
            clientId = CLIENT_ID,
            redirectUri = REDIRECT_URI,
            scope = SCOPE,
            codeChallenge = pkce.challenge,
            state = state,
        )

        launchBrowser(authorizeUrl)

        val params = awaitRedirect(REDIRECT_URI, timeoutMs) ?: return false
        val code = params["code"]?.takeIf { it.isNotBlank() } ?: return false
        if (params["state"] != null && params["state"] != state) return false

        val tokens = OAuthPkce.exchangeCode(httpFactory(current.cloudBaseUrl), CLIENT_ID, code, pkce.verifier, REDIRECT_URI)
            ?: return false

        setToken(tokens.accessToken)
        setEnabled(true)
        return true
    }

    suspend fun signOut() {
        setToken("")
    }

    private fun newState(random: SecureRandom = SecureRandom()): String =
        ByteArray(8).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    companion object {
        const val CLIENT_ID = "dorado-hd"
        const val REDIRECT_URI = "doradohd://oauth"
        const val SCOPE = "openid profile email dorado.api offline_access"
    }
}

/**
 * Bridges the browser redirect back into the suspended sign-in. The Android
 * activity forwards its deep-link intent to [onRedirect]; [await] suspends until
 * the redirect arrives or the timeout elapses.
 */
class CloudSignInCallback {
    private var pending: CompletableDeferred<Map<String, String>?>? = null

    suspend fun await(timeoutMs: Long): Map<String, String>? {
        val deferred = CompletableDeferred<Map<String, String>?>()
        pending = deferred
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            pending = null
        }
    }

    /** Returns true when the URI targeted our redirect scheme (and completed the wait). */
    fun onRedirect(scheme: String?, params: Map<String, String>): Boolean {
        if (!scheme.equals("doradohd", ignoreCase = true)) return false
        pending?.complete(params)
        return true
    }
}
