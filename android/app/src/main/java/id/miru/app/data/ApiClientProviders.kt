package id.miru.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

suspend fun ApiClient.getStreamProviders(): Result<List<StreamProviderItem>> = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder()
            .url("$BASE_URL/api/stream/providers")
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw Exception("Failed to load stream providers: HTTP ${resp.code}")
            }
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@runCatching emptyList()
            val list = mutableListOf<StreamProviderItem>()
            for (i in 0 until data.length()) {
                val obj = data.getJSONObject(i)
                list.add(
                    StreamProviderItem(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        hasSub = obj.optBoolean("has_sub", true),
                        hasDub = obj.optBoolean("has_dub", false),
                        isDefault = obj.optBoolean("is_default", false)
                    )
                )
            }
            list
        }
    }
}

suspend fun ApiClient.setDefaultStreamProvider(providerId: String): Result<Boolean> = withContext(Dispatchers.IO) {
    runCatching {
        val payload = JSONObject().apply {
            put("provider", providerId)
        }
        val request = Request.Builder()
            .url("$BASE_URL/api/stream/providers/default")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).execute().use { resp ->
            resp.isSuccessful
        }
    }
}

suspend fun ApiClient.getStreamServers(
    provider: String,
    slug: String,
    ep: Int,
    mode: String = "sub"
): Result<List<StreamServerItem>> = withContext(Dispatchers.IO) {
    runCatching {
        val encodedSlug = java.net.URLEncoder.encode(slug, "UTF-8")
        val encodedProv = java.net.URLEncoder.encode(provider, "UTF-8")
        val request = Request.Builder()
            .url("$BASE_URL/api/stream/servers?provider=$encodedProv&slug=$encodedSlug&ep=$ep&mode=$mode")
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw Exception("Failed to fetch stream servers: HTTP ${resp.code}")
            }
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@runCatching emptyList()
            val servers = mutableListOf<StreamServerItem>()
            for (i in 0 until data.length()) {
                val obj = data.getJSONObject(i)
                servers.add(
                    StreamServerItem(
                        serverId = obj.optString("server_id"),
                        serverName = obj.optString("server_name"),
                        latencyMs = obj.optLong("latency_ms", 0L)
                    )
                )
            }
            servers
        }
    }
}
