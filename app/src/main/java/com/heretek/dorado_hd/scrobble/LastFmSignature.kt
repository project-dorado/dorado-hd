package com.heretek.dorado_hd.scrobble

import java.security.MessageDigest

/**
 * Last.fm API request signing (the documented `api_sig` scheme): drop
 * `format`/`callback`/`api_sig`, sort the remaining parameters by name,
 * concatenate `name+value`, append the shared secret, then MD5.
 *
 * Pure and unit-tested — the network client is the only untested part.
 */
object LastFmSignature {

    const val API_ROOT = "https://ws.audioscrobbler.com/2.0/"

    fun apiSignature(params: Map<String, String>, secret: String): String {
        val raw = params.entries
            .filter { it.key != "format" && it.key != "callback" && it.key != "api_sig" }
            .sortedBy { it.key }
            .joinToString(separator = "") { it.key + it.value } + secret
        return md5Hex(raw)
    }

    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
