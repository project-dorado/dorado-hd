package com.heretek.dorado_hd.sync

import com.heretek.dorado_hd.cloud.CloudJson
import java.io.BufferedReader
import java.io.Closeable
import java.io.OutputStreamWriter
import java.net.Socket

/**
 * Line-framed JSON transport for the LAN sync protocol. The real implementation
 * wraps a TCP [Socket]; tests supply an in-memory pair. One UTF-8 JSON object
 * per line, matching the desktop `NetworkStreamLineTransport`.
 */
interface SyncLineTransport : Closeable {
    fun send(line: String)

    /** Returns the next line, or null when the peer closed the connection. */
    fun receive(): String?
}

/** [SyncLineTransport] over a connected TCP socket. */
class SocketSyncLineTransport(private val socket: Socket) : SyncLineTransport {
    private val reader: BufferedReader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
    private val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)

    override fun send(line: String) {
        writer.write(line)
        writer.write("\n")
        writer.flush()
    }

    override fun receive(): String? = reader.readLine()

    override fun close() {
        runCatching { reader.close() }
        runCatching { writer.close() }
        runCatching { socket.close() }
    }
}

// ---- wire DTOs (mirror Dorado.Infrastructure.Devices/SyncProtocol.cs) -----

data class SyncHelloRequest(
    val deviceId: String,
    val deviceName: String,
    val platform: String = "android",
    val appVersion: String,
)

data class SyncHelloResponse(
    val serverName: String,
    val serviceVersion: String,
    val serviceType: String,
    val port: Int,
    val pairingRequired: Boolean,
)

data class SyncPairResponse(val paired: Boolean, val sessionToken: String?, val reason: String?)

data class SyncRules(
    val music: String,
    val podcasts: String,
    val videos: String,
    val pictures: String,
)

data class SyncDeviceContent(val entityId: String, val category: String, val title: String, val sizeBytes: Long)

data class SyncManifestRequest(
    val deviceSerialNumber: String,
    val deviceName: String,
    val capacityBytes: Long,
    val systemBytes: Long,
    val guestSession: Boolean,
    val rules: SyncRules,
    val contents: List<SyncDeviceContent>,
)

data class SyncManifestItem(
    val action: String,
    val category: String,
    val entityId: String,
    val title: String,
    val sizeBytes: Long,
    val detail: String?,
)

data class SyncManifestResponse(
    val deviceSerialNumber: String,
    val isGuestSession: Boolean,
    val items: List<SyncManifestItem>,
    val addCount: Int,
    val removeCount: Int,
    val keepCount: Int,
    val totalAddBytes: Long,
    val totalRemoveBytes: Long,
)

data class SyncPullItem(
    val entityId: String,
    val category: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val rating: String?,
    val sizeBytes: Long,
    val sourcePath: String?,
    val detail: String?,
    /** Device-playable container to request, or null when copied verbatim. */
    val transcodeTarget: String?,
)

data class SyncPushItem(
    val entityId: String,
    val category: String,
    val title: String,
    val playCount: Int?,
    val rating: String?,
)

/**
 * Phone-side client for the desktop's LAN sync endpoint. Speaks the
 * `sync.*` JSON-RPC methods defined in [SyncProtocol]; callers are responsible
 * for opening the socket (mDNS-discovered `_dorado-sync._tcp`, port 8787).
 */
class SyncClient(private val transport: SyncLineTransport) : Closeable {

    private var nextId = 1

    fun hello(request: SyncHelloRequest): SyncHelloResponse {
        val map = call(SyncProtocol.METHOD_HELLO, helloBody(request))
        return SyncHelloResponse(
            serverName = CloudJson.stringOr(map, "serverName", ""),
            serviceVersion = CloudJson.stringOr(map, "serviceVersion", ""),
            serviceType = CloudJson.stringOr(map, "serviceType", ""),
            port = CloudJson.int(map, "port"),
            pairingRequired = CloudJson.bool(map, "pairingRequired"),
        )
    }

    fun pair(pairingCode: String, deviceId: String): SyncPairResponse {
        val map = call(SyncProtocol.METHOD_PAIR, mapOf("pairingCode" to pairingCode, "deviceId" to deviceId))
        return SyncPairResponse(
            paired = CloudJson.bool(map, "paired"),
            sessionToken = CloudJson.string(map, "sessionToken"),
            reason = CloudJson.string(map, "reason"),
        )
    }

    fun manifest(request: SyncManifestRequest): SyncManifestResponse {
        val map = call(SyncProtocol.METHOD_MANIFEST, manifestBody(request))
        return SyncManifestResponse(
            deviceSerialNumber = CloudJson.stringOr(map, "deviceSerialNumber", request.deviceSerialNumber),
            isGuestSession = CloudJson.bool(map, "isGuestSession"),
            items = CloudJson.asArray(map["items"]).map { item ->
                val obj = CloudJson.asObject(item)
                SyncManifestItem(
                    action = CloudJson.stringOr(obj, "action", ""),
                    category = CloudJson.stringOr(obj, "category", ""),
                    entityId = CloudJson.stringOr(obj, "entityId", ""),
                    title = CloudJson.stringOr(obj, "title", ""),
                    sizeBytes = CloudJson.long(obj, "sizeBytes"),
                    detail = CloudJson.string(obj, "detail"),
                )
            },
            addCount = CloudJson.int(map, "addCount"),
            removeCount = CloudJson.int(map, "removeCount"),
            keepCount = CloudJson.int(map, "keepCount"),
            totalAddBytes = CloudJson.long(map, "totalAddBytes"),
            totalRemoveBytes = CloudJson.long(map, "totalRemoveBytes"),
        )
    }

    fun pull(deviceSerialNumber: String): List<SyncPullItem> {
        val map = call(SyncProtocol.METHOD_PULL, mapOf("deviceSerialNumber" to deviceSerialNumber))
        return CloudJson.asArray(map["items"]).map { item ->
            val obj = CloudJson.asObject(item)
            SyncPullItem(
                entityId = CloudJson.stringOr(obj, "entityId", ""),
                category = CloudJson.stringOr(obj, "category", ""),
                title = CloudJson.stringOr(obj, "title", ""),
                artist = CloudJson.string(obj, "artist"),
                album = CloudJson.string(obj, "album"),
                rating = CloudJson.string(obj, "rating"),
                sizeBytes = CloudJson.long(obj, "sizeBytes"),
                sourcePath = CloudJson.string(obj, "sourcePath"),
                detail = CloudJson.string(obj, "detail"),
                transcodeTarget = CloudJson.string(obj, "transcodeTarget"),
            )
        }
    }

    fun push(deviceSerialNumber: String, items: List<SyncPushItem>): Int {
        val body = mapOf(
            "deviceSerialNumber" to deviceSerialNumber,
            "items" to items.map { item ->
                mapOf(
                    "entityId" to item.entityId,
                    "category" to item.category,
                    "title" to item.title,
                    "playCount" to item.playCount,
                    "rating" to item.rating,
                )
            },
        )
        return CloudJson.int(call(SyncProtocol.METHOD_PUSH, body), "accepted")
    }

    /** Synchronous JSON-RPC call: write the request, read the matching response. */
    private fun call(method: String, params: Map<String, Any?>): Map<String, Any?> {
        val id = nextId++
        val request = mapOf("jsonrpc" to SyncProtocol.JSONRPC, "id" to id.toString(), "method" to method, "params" to params)
        transport.send(CloudJson.write(request))

        // Read frames until we see the response for our id (the desktop only
        // sends responses, so this is normally the first line).
        while (true) {
            val line = transport.receive() ?: error("sync transport closed before a response")
            val root = CloudJson.asObject(CloudJson.parse(line))
            if (CloudJson.string(root, "id") != id.toString()) continue
            val error = CloudJson.asObject(root["error"])
            if (error.isNotEmpty()) {
                error("sync.$method failed: ${CloudJson.stringOr(error, "message", "unknown error")}")
            }
            return CloudJson.asObject(root["result"])
        }
    }

    private fun helloBody(request: SyncHelloRequest) = mapOf(
        "deviceId" to request.deviceId,
        "deviceName" to request.deviceName,
        "platform" to request.platform,
        "appVersion" to request.appVersion,
    )

    private fun manifestBody(request: SyncManifestRequest) = mapOf(
        "deviceSerialNumber" to request.deviceSerialNumber,
        "deviceName" to request.deviceName,
        "capacityBytes" to request.capacityBytes,
        "systemBytes" to request.systemBytes,
        "guestSession" to request.guestSession,
        "rules" to mapOf(
            "music" to request.rules.music,
            "podcasts" to request.rules.podcasts,
            "videos" to request.rules.videos,
            "pictures" to request.rules.pictures,
        ),
        "contents" to request.contents.map { content ->
            mapOf(
                "entityId" to content.entityId,
                "category" to content.category,
                "title" to content.title,
                "sizeBytes" to content.sizeBytes,
            )
        },
    )

    override fun close() = transport.close()
}
