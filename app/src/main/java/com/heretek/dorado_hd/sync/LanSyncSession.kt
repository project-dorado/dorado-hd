package com.heretek.dorado_hd.sync

/** Outcome of a live LAN pairing attempt (M8.2b). */
sealed interface LanSyncResult {
    data class Paired(
        val serverName: String,
        val serviceVersion: String,
        val sessionToken: String,
    ) : LanSyncResult

    /** The desktop rejected the pairing code (or another business rule). */
    data class Rejected(val reason: String) : LanSyncResult

    /** Transport/protocol failure (host unreachable, socket closed, RPC error). */
    data class Failed(val message: String) : LanSyncResult
}

/**
 * Live pairing against a discovered desktop: connects, runs `sync.hello`, then
 * `sync.pair` with the code shown by the desktop. Pure orchestration over a
 * [SyncConnector], so it is unit-testable with an in-memory transport.
 */
object LanSync {
    fun connectAndPair(
        connector: SyncConnector,
        host: String,
        port: Int,
        pairingCode: String,
        deviceId: String,
        deviceName: String,
        appVersion: String,
        platform: String = "android",
    ): LanSyncResult {
        val client = try {
            connector.connect(host, port, SyncConnector.DEFAULT_TIMEOUT_MS)
        } catch (e: Exception) {
            return LanSyncResult.Failed(e.message ?: "could not connect to $host:$port")
        }

        return try {
            val hello = client.hello(SyncHelloRequest(deviceId, deviceName, platform, appVersion))
            val pair = client.pair(pairingCode, deviceId)
            if (pair.paired && !pair.sessionToken.isNullOrBlank()) {
                LanSyncResult.Paired(hello.serverName, hello.serviceVersion, pair.sessionToken)
            } else {
                LanSyncResult.Rejected(pair.reason ?: "pairing rejected")
            }
        } catch (e: Exception) {
            LanSyncResult.Failed(e.message ?: "sync failed")
        } finally {
            runCatching { client.close() }
        }
    }
}
