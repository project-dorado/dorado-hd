package com.heretek.dorado_hd.sync

import java.net.InetSocketAddress
import java.net.Socket

/**
 * Opens a [SyncClient] to a discovered desktop. A seam so pairing can be tested
 * without a real socket.
 */
fun interface SyncConnector {
    fun connect(host: String, port: Int, timeoutMs: Int): SyncClient

    companion object {
        const val DEFAULT_TIMEOUT_MS = 4_000
    }
}

/** Plain-TCP [SyncConnector], matching the desktop `SyncTcpServer` framing. */
class TcpSyncConnector : SyncConnector {
    override fun connect(host: String, port: Int, timeoutMs: Int): SyncClient {
        val socket = Socket()
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.tcpNoDelay = true
        return SyncClient(SocketSyncLineTransport(socket))
    }
}
