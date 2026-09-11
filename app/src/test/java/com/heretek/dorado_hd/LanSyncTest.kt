package com.heretek.dorado_hd

import com.heretek.dorado_hd.sync.LanSync
import com.heretek.dorado_hd.sync.LanSyncResult
import com.heretek.dorado_hd.sync.SyncClient
import com.heretek.dorado_hd.sync.SyncConnector
import com.heretek.dorado_hd.sync.SyncLineTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanSyncTest {

    @Test
    fun `pairs and returns server info`() {
        val transport = FakeLine()
        transport.enqueue(HELLO_RESPONSE)
        transport.enqueue("""{"jsonrpc":"2.0","id":"2","result":{"paired":true,"sessionToken":"tok","reason":null}}""")

        val result = LanSync.connectAndPair(
            connector = SyncConnector { _, _, _ -> SyncClient(transport) },
            host = "192.168.1.10",
            port = 8787,
            pairingCode = "123456",
            deviceId = "dev-1",
            deviceName = "Pixel",
            appVersion = "0.1.0",
        )

        assertTrue(result is LanSyncResult.Paired)
        result as LanSyncResult.Paired
        assertEquals("Dorado Desktop", result.serverName)
        assertEquals("tok", result.sessionToken)
        assertTrue(transport.sent[0].contains("\"method\":\"sync.hello\""))
        assertTrue(transport.sent[1].contains("\"method\":\"sync.pair\""))
        assertTrue(transport.sent[1].contains("\"pairingCode\":\"123456\""))
    }

    @Test
    fun `rejected pairing surfaces the reason`() {
        val transport = FakeLine()
        transport.enqueue(HELLO_RESPONSE)
        transport.enqueue("""{"jsonrpc":"2.0","id":"2","result":{"paired":false,"sessionToken":null,"reason":"invalid pairing code"}}""")

        val result = LanSync.connectAndPair(
            SyncConnector { _, _, _ -> SyncClient(transport) },
            "host", 8787, "000000", "dev-1", "Pixel", "0.1.0",
        )

        assertTrue(result is LanSyncResult.Rejected)
        assertEquals("invalid pairing code", (result as LanSyncResult.Rejected).reason)
    }

    @Test
    fun `connect failure is reported`() {
        val result = LanSync.connectAndPair(
            SyncConnector { _, _, _ -> throw java.io.IOException("connection refused") },
            "host", 8787, "123456", "dev-1", "Pixel", "0.1.0",
        )

        assertTrue(result is LanSyncResult.Failed)
        assertTrue((result as LanSyncResult.Failed).message.contains("connection refused"))
    }

    @Test
    fun `rpc error during pairing is reported`() {
        val transport = FakeLine()
        transport.enqueue(HELLO_RESPONSE)
        transport.enqueue("""{"jsonrpc":"2.0","id":"2","error":{"code":-32603,"message":"invalid pairing code"}}""")

        val result = LanSync.connectAndPair(
            SyncConnector { _, _, _ -> SyncClient(transport) },
            "host", 8787, "000000", "dev-1", "Pixel", "0.1.0",
        )

        assertTrue(result is LanSyncResult.Failed)
        assertTrue((result as LanSyncResult.Failed).message.contains("invalid pairing code"))
    }

    private class FakeLine : SyncLineTransport {
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

    private companion object {
        const val HELLO_RESPONSE =
            """{"jsonrpc":"2.0","id":"1","result":{"serverName":"Dorado Desktop","serviceVersion":"0.1.0","serviceType":"_dorado-sync._tcp","port":8787,"pairingRequired":true}}"""
    }
}
