package com.heretek.dorado_hd

import com.heretek.dorado_hd.cloud.CloudSignIn
import com.heretek.dorado_hd.cloud.CloudSignInCallback
import com.heretek.dorado_hd.data.repo.DoradoSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the HD sign-in orchestration and the deep-link callback bridge. */
class CloudSignInTest {

    @Test
    fun signIn_exchangesCodeAndPersists() = runBlocking {
        var launchedUrl: String? = null
        var token: String? = null
        var enabled = false

        val http = FakeCloudHttp()
        http.enqueue(200, """{"access_token":"access-1","token_type":"Bearer","expires_in":3600,"refresh_token":"rt"}""")

        val signIn = CloudSignIn(
            currentSettings = { DoradoSettings(cloudEnabled = false, cloudBaseUrl = "https://cloud.example/") },
            setEnabled = { enabled = it },
            setToken = { token = it },
            launchBrowser = { launchedUrl = it },
            awaitRedirect = { _, _ -> mapOf("code" to "auth-code", "state" to stateOf(launchedUrl!!)) },
            httpFactory = { http },
        )

        assertTrue(signIn.signIn())
        assertTrue(launchedUrl!!.contains("/connect/authorize"))
        assertEquals("access-1", token)
        assertTrue(enabled)
    }

    @Test
    fun signIn_rejectsStateMismatch() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(200, """{"access_token":"access-1","token_type":"Bearer","expires_in":3600,"refresh_token":null}""")

        val signIn = CloudSignIn(
            currentSettings = { DoradoSettings(cloudEnabled = false, cloudBaseUrl = "https://cloud.example/") },
            setEnabled = { },
            setToken = { },
            launchBrowser = { },
            awaitRedirect = { _, _ -> mapOf("code" to "auth-code", "state" to "WRONG") },
            httpFactory = { http },
        )

        assertFalse(signIn.signIn())
    }

    @Test
    fun signIn_returnsFalseWithoutBaseUrl() = runBlocking {
        val signIn = CloudSignIn(
            currentSettings = { DoradoSettings(cloudEnabled = false, cloudBaseUrl = "") },
            setEnabled = { },
            setToken = { },
            launchBrowser = { },
            awaitRedirect = { _, _ -> emptyMap() },
        )

        assertFalse(signIn.signIn())
    }

    @Test
    fun signOut_clearsToken() = runBlocking {
        var token = "existing"
        val signIn = CloudSignIn(
            currentSettings = { DoradoSettings() },
            setEnabled = { },
            setToken = { token = it },
            launchBrowser = { },
            awaitRedirect = { _, _ -> null },
        )

        signIn.signOut()
        assertEquals("", token)
    }

    @Test
    fun callback_completesAwaitFromRedirect() = runBlocking {
        val callback = CloudSignInCallback()

        val deferred = async { callback.await(5_000) }
        delay(50)
        val handled = callback.onRedirect("doradohd", mapOf("code" to "c1", "state" to "s1"))

        assertTrue(handled)
        assertEquals("c1", deferred.await()?.get("code"))
    }

    @Test
    fun callback_ignoresForeignScheme() {
        val callback = CloudSignInCallback()
        assertFalse(callback.onRedirect("https", mapOf("code" to "c1")))
    }

    private fun stateOf(authorizeUrl: String): String =
        authorizeUrl.substringAfter("state=").substringBefore("&")
}
