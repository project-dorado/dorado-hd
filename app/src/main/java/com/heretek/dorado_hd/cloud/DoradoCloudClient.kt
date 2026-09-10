package com.heretek.dorado_hd.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kotlin client for the Dorado Cloud API, mirroring the typed surface of the
 * desktop `dorado-cloud/clients/DoradoCloud.Client/DoradoCloudClient.cs`.
 *
 * Every method is suspension-safe and returns `null` (or an empty list) on a
 * transport error or non-2xx response, so callers can fall back to the local
 * providers. The transport is an injected [CloudHttp] so the URL/JSON
 * composition is unit-testable without a network.
 */
class DoradoCloudClient(private val http: CloudHttp) {

    // ---- liveness --------------------------------------------------------

    suspend fun pingModule(module: String): CloudPing? = fetch("v1/$module/ping")?.let { map ->
        CloudPing(
            module = CloudJson.stringOr(map, "module", module),
            status = CloudJson.stringOr(map, "status", ""),
            version = CloudJson.stringOr(map, "version", ""),
        )
    }

    // ---- catalog ---------------------------------------------------------

    suspend fun catalogSearch(query: String, type: String = "release-group", limit: Int = 20): CloudCatalogSearch? {
        val path = "v1/catalog/search?q=${enc(query)}&type=${enc(type)}&limit=$limit"
        val map = fetch(path) ?: return null
        return CloudCatalogSearch(
            query = CloudJson.stringOr(map, "query", query),
            type = CloudJson.stringOr(map, "type", type),
            total = CloudJson.int(map, "total"),
            attribution = CloudJson.stringOr(map, "attribution", ""),
            items = CloudJson.asArray(map["items"]).mapNotNull { item ->
                val obj = CloudJson.asObject(item)
                val mbid = CloudJson.stringOr(obj, "mbid", "")
                if (mbid.isEmpty()) null else CloudCatalogItem(
                    type = CloudJson.stringOr(obj, "type", ""),
                    mbid = mbid,
                    title = CloudJson.stringOr(obj, "title", ""),
                    artist = CloudJson.stringOr(obj, "artist", ""),
                    date = CloudJson.stringOr(obj, "date", ""),
                    coverArtUrl = CloudJson.string(obj, "coverArtUrl"),
                )
            },
        )
    }

    suspend fun catalogArtist(mbid: String): CloudArtistDetail? = fetch("v1/catalog/artists/${enc(mbid)}")?.let { map ->
        CloudArtistDetail(
            mbid = CloudJson.stringOr(map, "mbid", mbid),
            name = CloudJson.stringOr(map, "name", ""),
            sortName = CloudJson.stringOr(map, "sortName", ""),
            country = CloudJson.stringOr(map, "country", ""),
            disambiguation = CloudJson.stringOr(map, "disambiguation", ""),
            coverArtUrl = CloudJson.string(map, "coverArtUrl"),
        )
    }

    /** Fetches front-cover bytes for a MusicBrainz release-group id. */
    suspend fun artworkFront(mbid: String, size: Int = 500): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { http.getBytes("v1/artwork/front/${enc(mbid)}?size=$size", token = null) }.getOrNull()
    }

    // ---- directory -------------------------------------------------------

    suspend fun directoryPodcastSearch(query: String, limit: Int = 20): CloudPodcastSearch? {
        val map = fetch("v1/directory/podcasts/search?q=${enc(query)}&limit=$limit") ?: return null
        return CloudPodcastSearch(
            query = CloudJson.stringOr(map, "query", query),
            total = CloudJson.int(map, "total"),
            configured = CloudJson.bool(map, "configured"),
            attribution = CloudJson.stringOr(map, "attribution", ""),
            items = CloudJson.asArray(map["items"]).map { item ->
                val obj = CloudJson.asObject(item)
                CloudPodcastResult(
                    feedId = CloudJson.stringOr(obj, "feedId", ""),
                    title = CloudJson.stringOr(obj, "title", ""),
                    author = CloudJson.stringOr(obj, "author", ""),
                    description = CloudJson.stringOr(obj, "description", ""),
                    imageUrl = CloudJson.stringOr(obj, "imageUrl", ""),
                    feedUrl = CloudJson.stringOr(obj, "feedUrl", ""),
                    categories = CloudJson.stringOr(obj, "categories", ""),
                    language = CloudJson.stringOr(obj, "language", ""),
                )
            },
        )
    }

    suspend fun directoryRadioSearch(
        query: String = "",
        country: String? = null,
        tag: String? = null,
        limit: Int = 30,
    ): CloudRadioSearch? {
        val params = mutableListOf("limit=$limit")
        if (query.isNotBlank()) params.add("q=${enc(query)}")
        if (!country.isNullOrBlank()) params.add("country=${enc(country)}")
        if (!tag.isNullOrBlank()) params.add("tag=${enc(tag)}")
        val map = fetch("v1/directory/radio/search?${params.joinToString("&")}") ?: return null
        return CloudRadioSearch(
            query = CloudJson.stringOr(map, "query", query),
            total = CloudJson.int(map, "total"),
            configured = CloudJson.bool(map, "configured"),
            attribution = CloudJson.stringOr(map, "attribution", ""),
            items = CloudJson.asArray(map["items"]).map { item ->
                val obj = CloudJson.asObject(item)
                CloudRadioStation(
                    stationId = CloudJson.stringOr(obj, "stationId", ""),
                    name = CloudJson.stringOr(obj, "name", ""),
                    url = CloudJson.stringOr(obj, "url", ""),
                    favicon = CloudJson.stringOr(obj, "favicon", ""),
                    country = CloudJson.stringOr(obj, "country", ""),
                    countryCode = CloudJson.stringOr(obj, "countryCode", ""),
                    tags = CloudJson.stringOr(obj, "tags", ""),
                    codec = CloudJson.stringOr(obj, "codec", ""),
                    bitrate = CloudJson.int(obj, "bitrate"),
                    votes = CloudJson.int(obj, "votes"),
                )
            },
        )
    }

    // ---- recs ------------------------------------------------------------

    suspend fun quickMix(seed: String, limit: Int = 20): CloudQuickMix? {
        val map = fetch("v1/recs/quickmix?seed=${enc(seed)}&limit=$limit") ?: return null
        return CloudQuickMix(
            seed = CloudJson.stringOr(map, "seed", seed),
            seedMbid = CloudJson.string(map, "seedMbid"),
            total = CloudJson.int(map, "total"),
            attribution = CloudJson.stringOr(map, "attribution", ""),
            items = CloudJson.asArray(map["items"]).map { item ->
                val obj = CloudJson.asObject(item)
                CloudQuickMixCandidate(
                    mbid = CloudJson.stringOr(obj, "mbid", ""),
                    name = CloudJson.stringOr(obj, "name", ""),
                    score = (obj["score"] as? Double) ?: 0.0,
                    reasons = CloudJson.asArray(obj["reasons"]).mapNotNull { it as? String },
                )
            },
        )
    }

    // ---- updates ---------------------------------------------------------

    suspend fun checkForUpdate(app: String, channel: String = "stable"): CloudUpdateCheck? {
        val map = fetch("v1/updates/${enc(app)}/${enc(channel)}") ?: return null
        val release = CloudJson.asObject(map["release"]).takeIf { it.isNotEmpty() }?.let { r ->
            CloudUpdateRelease(
                app = CloudJson.stringOr(r, "app", ""),
                channel = CloudJson.stringOr(r, "channel", ""),
                version = CloudJson.stringOr(r, "version", ""),
                url = CloudJson.stringOr(r, "url", ""),
                sha256 = CloudJson.stringOr(r, "sha256", ""),
                publishedAt = CloudJson.stringOr(r, "publishedAt", ""),
                notes = CloudJson.stringOr(r, "notes", ""),
                signature = CloudJson.stringOr(r, "signature", ""),
                algorithm = CloudJson.stringOr(r, "algorithm", ""),
            )
        }
        return CloudUpdateCheck(
            app = CloudJson.stringOr(map, "app", app),
            channel = CloudJson.stringOr(map, "channel", channel),
            available = CloudJson.bool(map, "available"),
            release = release,
            note = CloudJson.string(map, "note"),
        )
    }

    suspend fun updatesSigningKey(): String? = withContext(Dispatchers.IO) {
        runCatching { http.get("v1/updates/signing-key", token = null) }
            .getOrNull()
            ?.takeIf { it.isSuccess }
            ?.body
    }

    /**
     * Fetches the published signing key and verifies the release's detached
     * RS256 signature over the canonical manifest.
     */
    suspend fun verifyRelease(release: CloudUpdateRelease): Boolean {
        if (release.algorithm.isNotEmpty() && !release.algorithm.equals("RS256", ignoreCase = true)) return false
        val pem = updatesSigningKey() ?: return false
        val manifest = CloudUpdateManifest(
            app = release.app,
            channel = release.channel,
            version = release.version,
            url = release.url,
            sha256 = release.sha256,
            publishedAt = release.publishedAt,
            notes = release.notes,
        )
        return CloudUpdateVerifier.verify(manifest, release.signature, pem)
    }

    // ---- identity --------------------------------------------------------

    suspend fun getMe(token: String? = null): CloudMe? = fetch("v1/identity/me", token)?.let { map ->
        CloudMe(
            accountId = CloudJson.string(map, "accountId"),
            subject = CloudJson.string(map, "subject"),
            name = CloudJson.string(map, "name"),
            email = CloudJson.string(map, "email"),
        )
    }

    suspend fun listDevices(token: String? = null): List<CloudDevice> {
        val value = fetchValue("v1/identity/me/devices", token) ?: return emptyList()
        return CloudJson.asArray(value).map { device ->
            val obj = CloudJson.asObject(device)
            CloudDevice(
                id = CloudJson.stringOr(obj, "id", ""),
                name = CloudJson.stringOr(obj, "name", ""),
                platform = CloudJson.stringOr(obj, "platform", ""),
                serial = CloudJson.string(obj, "serial"),
                appVersion = CloudJson.string(obj, "appVersion"),
                createdAt = CloudJson.stringOr(obj, "createdAt", ""),
                lastSeenAt = CloudJson.stringOr(obj, "lastSeenAt", ""),
            )
        }
    }

    suspend fun registerDevice(
        name: String,
        platform: String,
        serial: String?,
        appVersion: String?,
        token: String? = null,
    ): CloudDevice? {
        val body = CloudJson.write(
            mapOf("name" to name, "platform" to platform, "serial" to serial, "appVersion" to appVersion)
        )
        val map = mutate("v1/identity/me/devices", "POST", body, token) ?: return null
        return CloudDevice(
            id = CloudJson.stringOr(map, "id", ""),
            name = CloudJson.stringOr(map, "name", name),
            platform = CloudJson.stringOr(map, "platform", platform),
            serial = CloudJson.string(map, "serial"),
            appVersion = CloudJson.string(map, "appVersion"),
            createdAt = CloudJson.stringOr(map, "createdAt", ""),
            lastSeenAt = CloudJson.stringOr(map, "lastSeenAt", ""),
        )
    }

    suspend fun removeDevice(deviceId: String, token: String? = null): Boolean = withContext(Dispatchers.IO) {
        runCatching { http.delete("v1/identity/me/devices/${enc(deviceId)}", token) }.getOrNull()?.isSuccess == true
    }

    suspend fun getSettings(token: String? = null): CloudSettings? = fetch("v1/identity/me/settings", token)?.let { map ->
        CloudSettings(
            payloadJson = CloudJson.stringOr(map, "payloadJson", "{}"),
            version = CloudJson.int(map, "version"),
            updatedAt = CloudJson.stringOr(map, "updatedAt", ""),
        )
    }

    suspend fun putSettings(payloadJson: String, expectedVersion: Int?, token: String? = null): CloudSettings? {
        val body = CloudJson.write(mapOf("payloadJson" to payloadJson, "expectedVersion" to expectedVersion))
        val map = mutate("v1/identity/me/settings", "PUT", body, token) ?: return null
        return CloudSettings(
            payloadJson = CloudJson.stringOr(map, "payloadJson", payloadJson),
            version = CloudJson.int(map, "version"),
            updatedAt = CloudJson.stringOr(map, "updatedAt", ""),
        )
    }

    // ---- social ----------------------------------------------------------

    suspend fun getProfile(handle: String): CloudProfile? = fetch("v1/social/profiles/${enc(handle)}")?.let(::toProfile)

    suspend fun getMyProfile(token: String? = null): CloudProfile? = fetch("v1/social/profiles/me", token)?.let(::toProfile)

    suspend fun getZuneCard(handle: String): CloudZuneCard? = fetch("v1/social/profiles/${enc(handle)}/zunecard")?.let { map ->
        CloudZuneCard(
            handle = CloudJson.stringOr(map, "handle", handle),
            displayName = CloudJson.stringOr(map, "displayName", ""),
            bio = CloudJson.stringOr(map, "bio", ""),
            followers = CloudJson.int(map, "followers"),
            following = CloudJson.int(map, "following"),
            activities = CloudJson.int(map, "activities"),
            badges = CloudJson.asArray(map["badges"]).map { badge ->
                val obj = CloudJson.asObject(badge)
                CloudBadge(
                    code = CloudJson.stringOr(obj, "code", ""),
                    name = CloudJson.stringOr(obj, "name", ""),
                    description = CloudJson.stringOr(obj, "description", ""),
                    earnedAt = CloudJson.string(obj, "earnedAt"),
                )
            },
            recent = CloudJson.asArray(map["recent"]).map { activity ->
                val obj = CloudJson.asObject(activity)
                CloudActivity(
                    id = CloudJson.stringOr(obj, "id", ""),
                    handle = CloudJson.stringOr(obj, "handle", ""),
                    kind = CloudJson.stringOr(obj, "kind", ""),
                    payloadJson = CloudJson.stringOr(obj, "payloadJson", "{}"),
                    createdAt = CloudJson.stringOr(obj, "createdAt", ""),
                )
            },
        )
    }

    suspend fun getFeed(limit: Int = 30, offset: Int = 0, token: String? = null): List<CloudActivity> {
        val value = fetchValue("v1/social/me/feed?limit=$limit&offset=$offset", token) ?: return emptyList()
        return CloudJson.asArray(value).map { activity ->
            val obj = CloudJson.asObject(activity)
            CloudActivity(
                id = CloudJson.stringOr(obj, "id", ""),
                handle = CloudJson.stringOr(obj, "handle", ""),
                kind = CloudJson.stringOr(obj, "kind", ""),
                payloadJson = CloudJson.stringOr(obj, "payloadJson", "{}"),
                createdAt = CloudJson.stringOr(obj, "createdAt", ""),
            )
        }
    }

    suspend fun postActivity(kind: String, payloadJson: String?, token: String? = null): CloudActivity? {
        val body = CloudJson.write(mapOf("kind" to kind, "payloadJson" to payloadJson))
        val map = mutate("v1/social/me/activities", "POST", body, token) ?: return null
        return CloudActivity(
            id = CloudJson.stringOr(map, "id", ""),
            handle = CloudJson.stringOr(map, "handle", ""),
            kind = CloudJson.stringOr(map, "kind", kind),
            payloadJson = CloudJson.stringOr(map, "payloadJson", "{}"),
            createdAt = CloudJson.stringOr(map, "createdAt", ""),
        )
    }

    suspend fun follow(handle: String, token: String? = null): Boolean = action("v1/social/profiles/${enc(handle)}/follow", "POST", token)

    suspend fun unfollow(handle: String, token: String? = null): Boolean = action("v1/social/profiles/${enc(handle)}/follow", "DELETE", token)

    suspend fun block(handle: String, token: String? = null): Boolean = action("v1/social/profiles/${enc(handle)}/block", "POST", token)

    suspend fun unblock(handle: String, token: String? = null): Boolean = action("v1/social/profiles/${enc(handle)}/block", "DELETE", token)

    // ---- internals -------------------------------------------------------

    private fun toProfile(map: Map<String, Any?>) = CloudProfile(
        accountId = CloudJson.stringOr(map, "accountId", ""),
        handle = CloudJson.stringOr(map, "handle", ""),
        displayName = CloudJson.stringOr(map, "displayName", ""),
        bio = CloudJson.stringOr(map, "bio", ""),
        followers = CloudJson.int(map, "followers"),
        following = CloudJson.int(map, "following"),
        activities = CloudJson.int(map, "activities"),
        isFollowing = CloudJson.bool(map, "isFollowing"),
        isBlocked = CloudJson.bool(map, "isBlocked"),
        createdAt = CloudJson.stringOr(map, "createdAt", ""),
    )

    private suspend fun fetch(path: String, token: String? = null): Map<String, Any?>? =
        CloudJson.asObject(fetchValue(path, token)).takeIf { it.isNotEmpty() }

    private suspend fun fetchValue(path: String, token: String? = null): Any? = withContext(Dispatchers.IO) {
        runCatching { http.get(path, token) }
            .getOrNull()
            ?.takeIf { it.isSuccess }
            ?.let { runCatching { CloudJson.parse(it.body) }.getOrNull() }
    }

    private suspend fun mutate(path: String, method: String, body: String, token: String?): Map<String, Any?>? =
        withContext(Dispatchers.IO) {
            val response = runCatching {
                when (method) {
                    "POST" -> http.post(path, body, token)
                    "PUT" -> http.put(path, body, token)
                    else -> error("unsupported method $method")
                }
            }.getOrNull() ?: return@withContext null
            if (!response.isSuccess) return@withContext null
            runCatching { CloudJson.asObject(CloudJson.parse(response.body)) }.getOrNull()
        }

    private suspend fun action(path: String, method: String, token: String?): Boolean = withContext(Dispatchers.IO) {
        val response = runCatching {
            if (method == "DELETE") http.delete(path, token) else http.post(path, null, token)
        }.getOrNull()
        response?.isSuccess == true
    }

    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
