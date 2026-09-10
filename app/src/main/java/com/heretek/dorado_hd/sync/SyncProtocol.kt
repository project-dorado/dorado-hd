package com.heretek.dorado_hd.sync

import java.security.SecureRandom

/**
 * Wire contract for phone ↔ Dorado desktop sync. Envelopes are JSON-RPC 2.0,
 * matching `dorado/src/Dorado.Plugins.Protocol`. Discovery is mDNS over
 * [_SERVICE_TYPE]; the socket transport, TLS and pairing handshake land in
 * M8.2b once the desktop exposes a sync endpoint.
 */
object SyncProtocol {
    const val JSONRPC = "2.0"
    const val SERVICE_TYPE = "_dorado-sync._tcp"
    const val DEFAULT_PORT = 8787

    const val METHOD_HELLO = "sync.hello"
    const val METHOD_PAIR = "sync.pair"
    const val METHOD_MANIFEST = "sync.manifest"
    const val METHOD_PULL = "sync.pull"
    const val METHOD_PUSH = "sync.push"

    /** A fresh 6-digit pairing code (leading zeros preserved). */
    fun newPairingCode(random: SecureRandom = SecureRandom()): String =
        "%06d".format(random.nextInt(1_000_000))
}
