package com.heretek.dorado_hd.cloud

import com.heretek.dorado_hd.data.repo.DoradoSettings

/**
 * Signed OTA update check against Dorado Cloud's published update feed, mirroring
 * the desktop `ICloudUpdateService`: fetches the latest release for the app and
 * channel, then verifies its detached RS256 signature before reporting it.
 *
 * Reads settings lazily so the cloud can be toggled at runtime; returns null when
 * disabled, unreachable, or already up to date. The transport is injected so the
 * check is unit-testable without a network.
 */
class CloudUpdateService(
    private val settings: () -> DoradoSettings,
    private val httpFactory: (String) -> CloudHttp = { base -> HttpUrlConnectionCloudHttp(base) },
) {
    data class UpdateInfo(
        val version: String,
        val notes: String,
        val url: String,
        val verified: Boolean,
    )

    fun isEnabled(): Boolean {
        val current = settings()
        return current.cloudEnabled && current.cloudBaseUrl.isNotBlank()
    }

    /** Latest release for [app]/[channel], or null when disabled/unreachable/up to date. */
    suspend fun check(app: String = "dorado-hd", channel: String = "stable"): UpdateInfo? {
        val current = settings()
        if (!current.cloudEnabled || current.cloudBaseUrl.isBlank()) return null

        val client = clientFor(current)
        val check = client.checkForUpdate(app, channel) ?: return null
        if (!check.available) return null

        val release = check.release ?: return null
        val verified = runCatching { client.verifyRelease(release) }.getOrDefault(false)
        return UpdateInfo(
            version = release.version,
            notes = release.notes,
            url = release.url,
            verified = verified,
        )
    }

    /** Rich result so callers can tell unreachable from up-to-date. */
    sealed interface UpdateStatus {
        data object Disabled : UpdateStatus
        data object Unreachable : UpdateStatus
        data object UpToDate : UpdateStatus
        data class Available(val version: String, val verified: Boolean) : UpdateStatus
    }

    suspend fun checkStatus(app: String = "dorado-hd", channel: String = "stable"): UpdateStatus {
        val current = settings()
        if (!current.cloudEnabled || current.cloudBaseUrl.isBlank()) return UpdateStatus.Disabled
        val client = clientFor(current)
        val check = client.checkForUpdate(app, channel) ?: return UpdateStatus.Unreachable
        if (!check.available) return UpdateStatus.UpToDate
        val release = check.release ?: return UpdateStatus.UpToDate
        return UpdateStatus.Available(
            version = release.version,
            verified = runCatching { client.verifyRelease(release) }.getOrDefault(false),
        )
    }

    private var cachedBaseUrl: String? = null
    private var cachedClient: DoradoCloudClient? = null

    private fun clientFor(current: DoradoSettings): DoradoCloudClient {
        if (cachedClient == null || cachedBaseUrl != current.cloudBaseUrl) {
            cachedBaseUrl = current.cloudBaseUrl
            cachedClient = DoradoCloudClient(httpFactory(current.cloudBaseUrl))
        }
        return cachedClient!!
    }
}
