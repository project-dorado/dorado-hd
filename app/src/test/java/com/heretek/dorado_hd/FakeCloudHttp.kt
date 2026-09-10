package com.heretek.dorado_hd

import com.heretek.dorado_hd.cloud.CloudHttp
import com.heretek.dorado_hd.cloud.CloudResponse

/** Captures requests and serves queued responses (shared by the cloud tests). */
internal class FakeCloudHttp : CloudHttp {
    var lastPath: String? = null
        private set
    var lastMethod: String? = null
        private set
    var lastAuth: String? = null
        private set
    var lastBody: String? = null
        private set

    private val responses = ArrayDeque<CloudResponse>()
    private var bytes: ByteArray? = null
    private var keyPem: String? = null

    fun enqueue(status: Int, body: String) {
        responses.addLast(CloudResponse(status, body))
    }

    fun enqueueBytes(value: ByteArray) {
        bytes = value
    }

    fun enqueueKey(pem: String) {
        keyPem = pem
    }

    override fun get(path: String, token: String?): CloudResponse {
        record(path, "GET", token, null)
        keyPem?.let { return CloudResponse(200, it) }
        return responses.removeFirst()
    }

    override fun post(path: String, body: String?, token: String?): CloudResponse {
        record(path, "POST", token, body)
        return responses.removeFirst()
    }

    override fun put(path: String, body: String, token: String?): CloudResponse {
        record(path, "PUT", token, body)
        return responses.removeFirst()
    }

    override fun delete(path: String, token: String?): CloudResponse {
        record(path, "DELETE", token, null)
        return if (responses.isEmpty()) CloudResponse(204, "") else responses.removeFirst()
    }

    var lastForm: Map<String, String>? = null

    override fun postForm(path: String, fields: Map<String, String>, token: String?): CloudResponse {
        record(path, "POST", token, null)
        lastForm = fields
        return if (responses.isEmpty()) CloudResponse(200, "{}") else responses.removeFirst()
    }

    override fun getBytes(path: String, token: String?): ByteArray? {
        record(path, "GET", token, null)
        return bytes
    }

    private fun record(path: String, method: String, token: String?, body: String?) {
        lastPath = path
        lastMethod = method
        lastAuth = if (token.isNullOrBlank()) null else "Bearer $token"
        lastBody = body
    }
}
