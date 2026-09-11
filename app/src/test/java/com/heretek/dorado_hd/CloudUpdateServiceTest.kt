package com.heretek.dorado_hd

import com.heretek.dorado_hd.cloud.CloudUpdateService
import com.heretek.dorado_hd.data.repo.DoradoSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudUpdateServiceTest {

    private fun settings(enabled: Boolean = true, baseUrl: String = "https://cloud.example/") =
        DoradoSettings(cloudEnabled = enabled, cloudBaseUrl = baseUrl)

    @Test
    fun check_returnsNullWhenDisabled() {
        val fake = FakeCloudHttp()
        val service = CloudUpdateService(settings = { settings(enabled = false) }, httpFactory = { fake })

        assertNull(runBlocking { service.check("dorado-hd") })
    }

    @Test
    fun check_returnsNullWhenBaseUrlBlank() {
        val fake = FakeCloudHttp()
        val service = CloudUpdateService(settings = { settings(baseUrl = "") }, httpFactory = { fake })

        assertNull(runBlocking { service.check("dorado-hd") })
    }

    @Test
    fun check_returnsNullWhenNoUpdateAvailable() {
        val fake = FakeCloudHttp()
        fake.enqueue(200, "{\"app\":\"dorado-hd\",\"channel\":\"stable\",\"available\":false}")
        val service = CloudUpdateService(settings = { settings() }, httpFactory = { fake })

        assertNull(runBlocking { service.check("dorado-hd") })
        assertEquals("v1/updates/dorado-hd/stable", fake.lastPath)
    }

    @Test
    fun check_returnsUnverifiedInfoWhenSignatureCannotBeVerified() {
        val fake = FakeCloudHttp()
        fake.enqueue(
            200,
            "{\"app\":\"dorado-hd\",\"channel\":\"stable\",\"available\":true,\"release\":{" +
                "\"app\":\"dorado-hd\",\"channel\":\"stable\",\"version\":\"1.2.3\"," +
                "\"url\":\"https://cloud.example/app.apk\",\"sha256\":\"ab\",\"notes\":\"fixes\"," +
                "\"signature\":\"sig\",\"algorithm\":\"RS256\"}}",
        )
        val service = CloudUpdateService(settings = { settings() }, httpFactory = { fake })

        val info = runBlocking { service.check("dorado-hd") }

        assertEquals("1.2.3", info?.version)
        assertEquals("fixes", info?.notes)
        assertEquals("https://cloud.example/app.apk", info?.url)
        assertFalse(info?.verified ?: true)
    }

    @Test
    fun isEnabled_tracksSettings() {
        val service = CloudUpdateService(settings = { settings() }, httpFactory = { FakeCloudHttp() })
        assertTrue(service.isEnabled())
    }
}
