package com.heretek.dorado_hd.net

import com.heretek.dorado_hd.scrobble.LastFmSignature
import com.heretek.dorado_hd.scrobble.Scrobble
import com.heretek.dorado_hd.scrobble.ScrobbleSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Last.fm `track.scrobble` over the documented signed-params API. Credentials
 * are supplied lazily from settings; a missing credential fails the send so the
 * scrobble stays queued. Network glue — the signing and queue are unit-tested.
 */
class LastFmClient(
    private val apiKey: () -> String,
    private val secret: () -> String,
    private val sessionKey: () -> String,
) : ScrobbleSink {

    override suspend fun scrobble(scrobble: Scrobble): Boolean = withContext(Dispatchers.IO) {
        val key = apiKey()
        val sec = secret()
        val sk = sessionKey()
        if (key.isBlank() || sec.isBlank() || sk.isBlank()) return@withContext false

        val params = linkedMapOf(
            "method" to "track.scrobble",
            "api_key" to key,
            "sk" to sk,
            "artist" to scrobble.artist,
            "track" to scrobble.title,
            "timestamp" to scrobble.timestampSec.toString(),
            "format" to "json",
        )
        if (scrobble.album.isNotBlank()) params["album"] = scrobble.album
        if (scrobble.durationSeconds > 0) params["duration"] = scrobble.durationSeconds.toString()
        params["api_sig"] = LastFmSignature.apiSignature(params, sec)

        runCatching {
            val connection = URL(LastFmSignature.API_ROOT).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            code in 200..299 && !text.contains("\"error\"")
        }.getOrDefault(false)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
