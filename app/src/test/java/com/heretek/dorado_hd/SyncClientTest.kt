package com.heretek.dorado_hd

import com.heretek.dorado_hd.cloud.CloudJson
import com.heretek.dorado_hd.sync.SyncClient
import com.heretek.dorado_hd.sync.SyncDeviceContent
import com.heretek.dorado_hd.sync.SyncHelloRequest
import com.heretek.dorado_hd.sync.SyncLineTransport
import com.heretek.dorado_hd.sync.SyncManifestRequest
import com.heretek.dorado_hd.sync.SyncPushItem
import com.heretek.dorado_hd.sync.SyncRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the phone-side sync client composes the documented JSON-RPC frames
 * and parses the desktop's camelCase responses. A [FakeSyncLineTransport] plays
 * the desktop side, so no socket is required.
 */
class SyncClientTest {

    @Test
    fun hello_parsesServiceInfo() {
        val transport = FakeSyncLineTransport()
        transport.enqueue(
            """{"jsonrpc":"2.0","id":"1","result":{"serverName":"Dorado Desktop","serviceVersion":"0.1.0","serviceType":"_dorado-sync._tcp","port":8787,"pairingRequired":true}}""",
        )
        val client = SyncClient(transport)

        val response = client.hello(SyncHelloRequest("dev-1", "Pixel", "android", "0.1.0"))

        assertEquals("Dorado Desktop", response.serverName)
        assertEquals(8787, response.port)
        assertTrue(response.pairingRequired)
        assertTrue(transport.sent[0].contains("\"method\":\"sync.hello\""))
        assertTrue(transport.sent[0].contains("\"deviceId\":\"dev-1\""))
    }

    @Test
    fun pair_parsesToken() {
        val transport = FakeSyncLineTransport()
        transport.enqueue("""{"jsonrpc":"2.0","id":"1","result":{"paired":true,"sessionToken":"tok","reason":null}}""")
        val client = SyncClient(transport)

        val response = client.pair("123456", "dev-1")

        assertTrue(response.paired)
        assertEquals("tok", response.sessionToken)
        assertTrue(transport.sent[0].contains("\"pairingCode\":\"123456\""))
    }

    @Test
    fun manifest_sendsSnapshotAndParsesPlan() {
        val transport = FakeSyncLineTransport()
        transport.enqueue(
            """{"jsonrpc":"2.0","id":"1","result":{"deviceSerialNumber":"SERIAL-1","isGuestSession":false,"items":[{"action":"ADD","category":"MUSIC","entityId":"e1","title":"Track A","sizeBytes":100,"detail":"Artist — Album"},{"action":"KEEP","category":"MUSIC","entityId":"e2","title":"Track B","sizeBytes":200,"detail":null}],"addCount":1,"removeCount":0,"keepCount":1,"totalAddBytes":100,"totalRemoveBytes":0}}""",
        )
        val client = SyncClient(transport)

        val response = client.manifest(
            SyncManifestRequest(
                deviceSerialNumber = "SERIAL-1",
                deviceName = "Pixel",
                capacityBytes = 16L * 1024 * 1024 * 1024,
                systemBytes = 1024,
                guestSession = false,
                rules = SyncRules("All Music (Automatic Sync)", "3 Newest Episodes", "All Videos & Pictures", "Newest 25 Items"),
                contents = listOf(SyncDeviceContent("e2", "MUSIC", "Track B", 200)),
            ),
        )

        assertEquals("SERIAL-1", response.deviceSerialNumber)
        assertEquals(1, response.addCount)
        assertEquals(1, response.keepCount)
        assertEquals(2, response.items.size)
        assertEquals("ADD", response.items[0].action)
        val body = transport.sent[0]
        assertTrue(body.contains("\"entityId\":\"e2\""))
        assertTrue(body.contains("\"rules\":{"))
        assertTrue(body.contains("\"music\":\"All Music (Automatic Sync)\""))
    }

    @Test
    fun pull_parsesMetadataRatingAndTranscodeTarget() {
        val transport = FakeSyncLineTransport()
        transport.enqueue(
            """{"jsonrpc":"2.0","id":"1","result":{"deviceSerialNumber":"SERIAL-1","items":[{"entityId":"e1","category":"MUSIC","title":"Track A","artist":"Artist A","album":"Album A","rating":"HEART","sizeBytes":100,"sourcePath":"/music/a.wma","detail":"Artist A — Album A","transcodeTarget":"m4a"},{"entityId":"e2","category":"MUSIC","title":"Track B","artist":"Artist B","album":"Album B","rating":"NONE","sizeBytes":200,"sourcePath":"/music/b.mp3","detail":null,"transcodeTarget":null}]}}""",
        )
        val client = SyncClient(transport)

        val items = client.pull("SERIAL-1")

        assertEquals(2, items.size)
        assertEquals("HEART", items[0].rating)
        assertEquals("/music/a.wma", items[0].sourcePath)
        assertEquals("m4a", items[0].transcodeTarget)
        assertNull(items[1].transcodeTarget)
    }

    @Test
    fun push_sendsPlayCountsAndRatings() {
        val transport = FakeSyncLineTransport()
        transport.enqueue("""{"jsonrpc":"2.0","id":"1","result":{"accepted":2}}""")
        val client = SyncClient(transport)

        val accepted = client.push(
            "SERIAL-1",
            listOf(
                SyncPushItem("e1", "MUSIC", "Track A", 5, "HEART"),
                SyncPushItem("e2", "MUSIC", "Track B", 0, "BROKEN"),
            ),
        )

        assertEquals(2, accepted)
        val body = transport.sent[0]
        assertTrue(body.contains("\"playCount\":5"))
        assertTrue(body.contains("\"rating\":\"BROKEN\""))
    }

    @Test
    fun errorResponse_throws() {
        val transport = FakeSyncLineTransport()
        transport.enqueue("""{"jsonrpc":"2.0","id":"1","error":{"code":-32603,"message":"invalid pairing code"}}""")
        val client = SyncClient(transport)

        val error = assertThrows(IllegalStateException::class.java) { client.pair("000000", "dev-1") }
        assertTrue(error.message!!.contains("invalid pairing code"))
    }

    @Test
    fun requestId_isIncremented() {
        val transport = FakeSyncLineTransport()
        transport.enqueue("""{"jsonrpc":"2.0","id":"1","result":{"serverName":"s","serviceVersion":"v","serviceType":"t","port":1,"pairingRequired":false}}""")
        transport.enqueue("""{"jsonrpc":"2.0","id":"2","result":{"paired":true,"sessionToken":"t","reason":null}}""")
        val client = SyncClient(transport)

        client.hello(SyncHelloRequest("d", "n", "android", "0.1.0"))
        client.pair("123456", "d")

        assertTrue(transport.sent[0].contains("\"id\":\"1\""))
        assertTrue(transport.sent[1].contains("\"id\":\"2\""))
        assertNotNull(CloudJson.parse(transport.sent[1]))
    }

    private class FakeSyncLineTransport : SyncLineTransport {
        val sent = mutableListOf<String>()
        private val responses = ArrayDeque<String>()

        fun enqueue(response: String) {
            responses.addLast(response)
        }

        override fun send(line: String) {
            sent.add(line)
        }

        override fun receive(): String? = if (responses.isEmpty()) null else responses.removeFirst()

        override fun close() = Unit
    }
}
