package com.heretek.dorado_hd.cloud

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** A raw HTTP response from the cloud gateway. */
data class CloudResponse(val status: Int, val body: String) {
    val isSuccess: Boolean get() = status in 200..299
}

/**
 * Transport seam for the cloud client so unit tests can assert URL/JSON
 * composition without a network. Implementations are synchronous and are
 * expected to be invoked from `Dispatchers.IO`.
 */
interface CloudHttp {
    fun get(path: String, token: String?): CloudResponse
    fun post(path: String, body: String?, token: String?): CloudResponse
    fun put(path: String, body: String, token: String?): CloudResponse
    fun delete(path: String, token: String?): CloudResponse

    /** POSTs `application/x-www-form-urlencoded` (the OAuth token endpoint). */
    fun postForm(path: String, fields: Map<String, String>, token: String?): CloudResponse

    /** Fetches raw bytes (artwork). Returns null on a non-2xx response or failure. */
    fun getBytes(path: String, token: String?): ByteArray?
}

/**
 * `HttpURLConnection` implementation, matching the rest of the `net/` layer
 * (no OkHttp/Retrofit dependency at this layer). `baseUrl` is user-supplied
 * (the official instance or a self-hosted deployment).
 */
class HttpUrlConnectionCloudHttp(
    private val baseUrl: String,
    private val userAgent: String = "DoradoHD/0.1 (android; sister-app to dorado)",
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000,
) : CloudHttp {

    override fun get(path: String, token: String?): CloudResponse = execute("GET", path, null, token)

    override fun post(path: String, body: String?, token: String?): CloudResponse = execute("POST", path, body, token)

    override fun put(path: String, body: String, token: String?): CloudResponse = execute("PUT", path, body, token)

    override fun delete(path: String, token: String?): CloudResponse = execute("DELETE", path, null, token)

    override fun postForm(path: String, fields: Map<String, String>, token: String?): CloudResponse {
        val url = URL(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            connection.doOutput = true
            val body = fields.entries.joinToString("&") {
                "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { input -> BufferedReader(input.reader(Charsets.UTF_8)).use { it.readText() } } ?: ""
            return CloudResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }

    override fun getBytes(path: String, token: String?): ByteArray? {
        val url = URL(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("User-Agent", userAgent)
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            if (connection.responseCode !in 200..299) return null
            return connection.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun execute(method: String, path: String, body: String?, token: String?): CloudResponse {
        val url = URL(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { input ->
                BufferedReader(input.reader(Charsets.UTF_8)).use { it.readText() }
            } ?: ""
            return CloudResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }
}
