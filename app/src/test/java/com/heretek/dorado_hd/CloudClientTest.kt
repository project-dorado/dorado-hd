package com.heretek.dorado_hd

import com.heretek.dorado_hd.cloud.CloudJson
import com.heretek.dorado_hd.cloud.CloudUpdateManifest
import com.heretek.dorado_hd.cloud.CloudUpdateVerifier
import com.heretek.dorado_hd.cloud.DoradoCloudClient
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Dorado Cloud Kotlin client. A [FakeCloudHttp] captures the
 * composed requests and serves canned JSON, so no network or Android runtime is
 * required — these run under plain `testDebugUnitTest`.
 */
class CloudClientTest {

    // ---- canonical JSON: .NET JavaScriptEncoder.Default parity -----------

    @Test
    fun canonicalJson_matchesDotNetVector_specialCharacters() {
        val manifest = CloudUpdateManifest(
            app = "dorado",
            channel = "stable",
            version = "0.4.0",
            url = "https://example.invalid/dorado-0.4.0.zip?a=1&b=2",
            sha256 = "abc123def456",
            publishedAt = "2026-09-10T17:10:20+00:00",
            notes = "Notes with <angle> & 'quote' + plus",
        )

        assertEquals(
            """{"app":"dorado","channel":"stable","version":"0.4.0","url":"https://example.invalid/dorado-0.4.0.zip?a=1\u0026b=2","sha256":"abc123def456","publishedAt":"2026-09-10T17:10:20+00:00","notes":"Notes with \u003Cangle\u003E \u0026 \u0027quote\u0027 \u002B plus"}""",
            CloudUpdateVerifier.canonicalManifestJson(manifest),
        )
    }

    @Test
    fun canonicalJson_matchesDotNetVector_printableAsciiAndUnicode() {
        val notes = (0x20..0x7E).map { it.toChar() }.joinToString("") + "\u00E9\u20AC"
        val manifest = CloudUpdateManifest("a", "b", "c", "u", "s", "2026-01-02T03:04:05+00:00", notes)

        assertEquals(
            """{"app":"a","channel":"b","version":"c","url":"u","sha256":"s","publishedAt":"2026-01-02T03:04:05+00:00","notes":" !\u0022#$%\u0026\u0027()*\u002B,-./0123456789:;\u003C=\u003E?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_\u0060abcdefghijklmnopqrstuvwxyz{|}~\u00E9\u20AC"}""",
            CloudUpdateVerifier.canonicalManifestJson(manifest),
        )
    }

    @Test
    fun canonicalJson_matchesDotNetVector_controlCharacters() {
        val manifest = CloudUpdateManifest(
            "a", "b", "c", "u", "s", "2026-01-02T03:04:05+00:00",
            "tab\t nl\n cr\r bs\b ff\u000C bell\u0007 vt\u000B esc\u001B",
        )

        assertEquals(
            """{"app":"a","channel":"b","version":"c","url":"u","sha256":"s","publishedAt":"2026-01-02T03:04:05+00:00","notes":"tab\t nl\n cr\r bs\b ff\f bell\u0007 vt\u000B esc\u001B"}""",
            CloudUpdateVerifier.canonicalManifestJson(manifest),
        )
    }

    // ---- RSA signature verification -------------------------------------

    @Test
    fun verify_acceptsSignatureOverCanonicalBytes() {
        val keyPair = generateKeyPair()
        val manifest = CloudUpdateManifest(
            "dorado", "stable", "0.5.0",
            "https://example.invalid/dorado-0.5.0.zip",
            "deadbeef", "2026-09-11T00:00:00+00:00", "notes & more",
        )
        val signature = sign(CloudUpdateVerifier.canonicalManifestJson(manifest), keyPair)

        assertTrue(CloudUpdateVerifier.verify(manifest, signature, publicKeyPem(keyPair)))
    }

    @Test
    fun verify_rejectsTamperedManifest() {
        val keyPair = generateKeyPair()
        val manifest = CloudUpdateManifest(
            "dorado", "stable", "0.5.0",
            "https://example.invalid/dorado-0.5.0.zip",
            "deadbeef", "2026-09-11T00:00:00+00:00", "notes",
        )
        val signature = sign(CloudUpdateVerifier.canonicalManifestJson(manifest), keyPair)
        val tampered = manifest.copy(version = "0.5.1")

        assertFalse(CloudUpdateVerifier.verify(tampered, signature, publicKeyPem(keyPair)))
    }

    // ---- client request/parse behaviour ---------------------------------

    @Test
    fun catalogSearch_buildsPathAndParsesItems() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(
            200,
            """{"query":"mb","type":"artist","total":1,"attribution":"MusicBrainz","items":[{"type":"artist","mbid":"abc","title":"MB","artist":"MB","date":"","coverArtUrl":null}]}""",
        )
        val client = DoradoCloudClient(http)

        val result = client.catalogSearch("mb", "artist", limit = 10)

        assertEquals("v1/catalog/search?q=mb&type=artist&limit=10", http.lastPath)
        assertNotNull(result)
        assertEquals(1, result!!.items.size)
        assertEquals("abc", result.items[0].mbid)
    }

    @Test
    fun catalogSearch_escapesQuery() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(200, """{"query":"a b/c","type":"release-group","total":0,"attribution":"","items":[]}""")
        val client = DoradoCloudClient(http)

        client.catalogSearch("a b/c")

        assertTrue(http.lastPath!!.contains("q=a%20b%2Fc"))
    }

    @Test
    fun artworkFront_returnsBytes() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueueBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        val client = DoradoCloudClient(http)

        val bytes = client.artworkFront("rg-1", size = 250)

        assertEquals("v1/artwork/front/rg-1?size=250", http.lastPath)
        assertEquals(4, bytes!!.size)
    }

    @Test
    fun directoryPodcastSearch_parsesConfiguredFlag() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(200, """{"query":"tech","total":0,"configured":false,"attribution":"Podcast Index","items":[]}""")
        val client = DoradoCloudClient(http)

        val result = client.directoryPodcastSearch("tech")

        assertNotNull(result)
        assertFalse(result!!.configured)
    }

    @Test
    fun checkForUpdate_parsesRelease() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(
            200,
            """{"app":"dorado-hd","channel":"stable","available":true,"release":{"app":"dorado-hd","channel":"stable","version":"0.2.0","url":"https://example.invalid/hd-0.2.0.apk","sha256":"aa","publishedAt":"2026-09-11T00:00:00+00:00","notes":"n","signature":"sig","algorithm":"RS256"},"note":null}""",
        )
        val client = DoradoCloudClient(http)

        val check = client.checkForUpdate("dorado-hd")

        assertEquals("v1/updates/dorado-hd/stable", http.lastPath)
        assertNotNull(check)
        assertTrue(check!!.available)
        assertEquals("0.2.0", check.release!!.version)
    }

    @Test
    fun verifyRelease_fetchesKeyAndValidates() = runBlocking {
        val keyPair = generateKeyPair()
        val release = com.heretek.dorado_hd.cloud.CloudUpdateRelease(
            app = "dorado-hd", channel = "stable", version = "0.2.0",
            url = "https://example.invalid/hd-0.2.0.apk", sha256 = "aa",
            publishedAt = "2026-09-11T00:00:00+00:00", notes = "n",
            signature = "", algorithm = "RS256",
        )
        val manifest = CloudUpdateManifest(
            release.app, release.channel, release.version, release.url, release.sha256, release.publishedAt, release.notes,
        )
        val signed = release.copy(signature = sign(CloudUpdateVerifier.canonicalManifestJson(manifest), keyPair))

        val http = FakeCloudHttp()
        http.enqueueKey(publicKeyPem(keyPair))
        val client = DoradoCloudClient(http)

        assertTrue(client.verifyRelease(signed))
        assertEquals("v1/updates/signing-key", http.lastPath)
    }

    @Test
    fun getMe_parsesPrincipal() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(200, """{"accountId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","subject":"s","name":"jane","email":"jane@example.com"}""")
        val client = DoradoCloudClient(http)

        val me = client.getMe(token = "tok")

        assertEquals("v1/identity/me", http.lastPath)
        assertEquals("jane@example.com", me!!.email)
        assertEquals("Bearer tok", http.lastAuth)
    }

    @Test
    fun getZuneCard_parsesBadgesAndRecent() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(
            200,
            """{"handle":"jane","displayName":"Jane","bio":"b","followers":2,"following":1,"activities":3,"badges":[{"code":"connector","name":"Connector","description":"d","earnedAt":"2026-01-01T00:00:00+00:00"}],"recent":[{"id":"a1","handle":"jane","kind":"listen","payloadJson":"{}","createdAt":"2026-01-02T00:00:00+00:00"}]}""",
        )
        val client = DoradoCloudClient(http)

        val card = client.getZuneCard("jane")

        assertEquals("v1/social/profiles/jane/zunecard", http.lastPath)
        assertEquals(1, card!!.badges.size)
        assertEquals(1, card.recent.size)
    }

    @Test
    fun postActivity_sendsBodyAndParses() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(201, """{"id":"a1","handle":"","kind":"listen","payloadJson":"{\"track\":\"x\"}","createdAt":"2026-01-02T00:00:00+00:00"}""")
        val client = DoradoCloudClient(http)

        val activity = client.postActivity("listen", "{\"track\":\"x\"}", token = "tok")

        assertEquals("v1/social/me/activities", http.lastPath)
        assertEquals("POST", http.lastMethod)
        assertTrue(http.lastBody!!.contains("\"kind\":\"listen\""))
        assertEquals("a1", activity!!.id)
    }

    @Test
    fun failure_returnsNull() = runBlocking {
        val http = FakeCloudHttp()
        http.enqueue(500, "boom")
        val client = DoradoCloudClient(http)

        assertNull(client.catalogSearch("x"))
    }

    @Test
    fun cloudJson_roundTripsRequestBodies() {
        val json = CloudJson.write(mapOf("name" to "Pixel", "serial" to null, "version" to 3))
        assertEquals("""{"name":"Pixel","serial":null,"version":3}""", json)
        val parsed = CloudJson.asObject(CloudJson.parse(json))
        assertEquals("Pixel", CloudJson.string(parsed, "name"))
        assertNull(CloudJson.string(parsed, "serial"))
    }

    // ---- helpers ---------------------------------------------------------

    private fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun sign(canonical: String, keyPair: KeyPair): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(keyPair.private)
        signature.update(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    private fun publicKeyPem(keyPair: KeyPair): String {
        val encoded = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        return buildString {
            append("-----BEGIN PUBLIC KEY-----\n")
            encoded.chunked(64).forEach { append(it).append('\n') }
            append("-----END PUBLIC KEY-----\n")
        }
    }
}
