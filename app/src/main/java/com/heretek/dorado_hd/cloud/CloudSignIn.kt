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
    /**
     * Human-readable reason the last [signIn] failed, or null when it succeeded
     * or has not run. Surfaced by the settings screen (e.g. an unverified email
     * comes back as `access_denied`).
     */
    var lastError: String? = null
        private set

    suspend fun signIn(): Boolean {
        lastError = null
        val current = currentSettings()
        if (current.cloudBaseUrl.isBlank()) {
            lastError = "cloud url is not configured"
            return false
        }

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

        val params = awaitRedirect(REDIRECT_URI, timeoutMs) ?: run {
            lastError = "sign-in timed out"
            return false
        }
        // The state must match exactly (a missing state is not acceptable).
        if (params["state"] != state) {
            lastError = "sign-in state did not match"
            return false
        }
        // An error response (e.g. access_denied for an unverified email) carries
        // no code; surface the server's description instead of failing silently.
        params["error"]?.takeIf { it.isNotBlank() }?.let { error ->
            lastError = describeError(error, params["error_description"])
            return false
        }
        val code = params["code"]?.takeIf { it.isNotBlank() } ?: run {
            lastError = "no authorization code returned"
            return false
        }

        // The token exchange is blocking HttpURLConnection work; keep it off
        // the main thread or the caller sees NetworkOnMainThreadException.
        val tokens = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            OAuthPkce.exchangeCode(httpFactory(current.cloudBaseUrl), CLIENT_ID, code, pkce.verifier, REDIRECT_URI)
        } ?: run {
            lastError = "token exchange failed"
            return false
        }

        setToken(tokens.accessToken)
        setEnabled(true)
        return true
    }

    suspend fun signOut() {
        setToken("")
        // Disabling prevents unauthenticated requests after sign-out.
        setEnabled(false)
    }

    private fun describeError(error: String, description: String?): String {
        val detail = description?.takeIf { it.isNotBlank() }
        return when (error) {
            "access_denied" -> detail?.let { "sign-in denied — $it" } ?: "sign-in denied"
            else -> detail?.let { "$error — $it" } ?: error
        }
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
