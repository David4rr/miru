package id.miru.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object ApiClientSubtitles {

    suspend fun fetchAnimeToshoSubtitles(malId: Long?, title: String, episode: Int): Result<List<ExternalSubtitleSource>> = withContext(Dispatchers.IO) {
        runCatching {
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val midParam = if (malId != null && malId > 0) "&mal_id=$malId" else ""
            val url = "${ApiClient.BASE_URL}/api/subtitles/sources/animetosho?title=$encTitle&episode=$episode$midParam"
            val req = Request.Builder().url(url).get().build()
            ApiClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val arr = json.optJSONArray("data") ?: JSONArray()
                parseExternalSources(arr)
            }
        }
    }

    suspend fun fetchOpenSubtitles(query: String, episode: Int, languages: String = "id,en"): Result<List<ExternalSubtitleSource>> = withContext(Dispatchers.IO) {
        runCatching {
            val encQuery = URLEncoder.encode(query, "UTF-8")
            val encLangs = URLEncoder.encode(languages, "UTF-8")
            val url = "${ApiClient.BASE_URL}/api/subtitles/sources/opensubtitles?query=$encQuery&episode=$episode&languages=$encLangs"
            val req = Request.Builder().url(url).get().build()
            ApiClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val arr = json.optJSONArray("data") ?: JSONArray()
                parseExternalSources(arr)
            }
        }
    }

    suspend fun requestAiFansub(
        vttUrl: String,
        providerId: String? = null,
        sourceLang: String = "en",
        targetLang: String = "id",
        batchSize: Int = 40
    ): Result<AiFansubResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("vtt_url", vttUrl)
                put("provider_id", providerId ?: JSONObject.NULL)
                put("source_lang", sourceLang)
                put("target_lang", targetLang)
                put("batch_size", batchSize)
            }
            val req = Request.Builder()
                .url("${ApiClient.BASE_URL}/api/subtitles/ai/fansub")
                .post(payload.toString().toRequestBody(ApiClient.JSON_MEDIA))
                .build()
            ApiClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.message}")
                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val data = json.getJSONObject("data")
                AiFansubResponse(
                    cached = data.optBoolean("cached", false),
                    cuesTranslated = data.optInt("cues_translated", 0),
                    durationMs = data.optLong("duration_ms", 0L),
                    vttContent = data.optString("vtt_content", "")
                )
            }
        }
    }

    suspend fun getAiProviders(): Result<List<AiProviderConfig>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${ApiClient.BASE_URL}/api/subtitles/ai/providers").get().build()
            ApiClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val arr = json.optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<AiProviderConfig>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        AiProviderConfig(
                            providerType = obj.optString("provider_type", ""),
                            baseUrl = obj.optString("base_url").takeIf { it.isNotEmpty() },
                            apiKey = obj.optString("api_key").takeIf { it.isNotEmpty() },
                            modelId = obj.optString("model_id", ""),
                            promptTemplate = obj.optString("prompt_template").takeIf { it.isNotEmpty() },
                            isActive = obj.optBoolean("is_active", false)
                        )
                    )
                }
                list
            }
        }
    }

    suspend fun saveAiProvider(config: AiProviderConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("provider_type", config.providerType)
                put("base_url", config.baseUrl ?: JSONObject.NULL)
                put("api_key", config.apiKey ?: JSONObject.NULL)
                put("model_id", config.modelId)
                put("prompt_template", config.promptTemplate ?: JSONObject.NULL)
                put("is_active", config.isActive)
            }
            val req = Request.Builder()
                .url("${ApiClient.BASE_URL}/api/subtitles/ai/providers")
                .post(payload.toString().toRequestBody(ApiClient.JSON_MEDIA))
                .build()
            ApiClient.client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    suspend fun getSubtitleOffset(slug: String, episode: Int): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val encSlug = URLEncoder.encode(slug, "UTF-8")
            val req = Request.Builder().url("${ApiClient.BASE_URL}/api/subtitles/offset?slug=$encSlug&episode=$episode").get().build()
            ApiClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use 0
                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                json.optInt("offset_ms", 0)
            }
        }
    }

    suspend fun saveSubtitleOffset(slug: String, episode: Int, offsetMs: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("slug", slug)
                put("episode", episode)
                put("offset_ms", offsetMs)
            }
            val req = Request.Builder()
                .url("${ApiClient.BASE_URL}/api/subtitles/offset")
                .post(payload.toString().toRequestBody(ApiClient.JSON_MEDIA))
                .build()
            ApiClient.client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    suspend fun getLexiconRules(): Result<List<LexiconRuleItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${ApiClient.BASE_URL}/api/subtitles/lexicon").get().build()
            ApiClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val arr = json.optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<LexiconRuleItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        LexiconRuleItem(
                            id = obj.optLong("id", 0L),
                            targetTerm = obj.optString("target_term", ""),
                            replacement = obj.optString("replacement", ""),
                            category = obj.optString("category", "anime"),
                            isRegex = obj.optBoolean("is_regex", false)
                        )
                    )
                }
                list
            }
        }
    }

    suspend fun saveLexiconRule(targetTerm: String, replacement: String, category: String = "anime", isRegex: Boolean = false): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("target_term", targetTerm)
                put("replacement", replacement)
                put("category", category)
                put("is_regex", isRegex)
            }
            val req = Request.Builder()
                .url("${ApiClient.BASE_URL}/api/subtitles/lexicon")
                .post(payload.toString().toRequestBody(ApiClient.JSON_MEDIA))
                .build()
            ApiClient.client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    suspend fun deleteLexiconRule(id: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${ApiClient.BASE_URL}/api/subtitles/lexicon?id=$id").delete().build()
            ApiClient.client.newCall(req).execute().use { it.isSuccessful }
        }
    }

    private fun parseExternalSources(arr: JSONArray): List<ExternalSubtitleSource> {
        val list = mutableListOf<ExternalSubtitleSource>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                ExternalSubtitleSource(
                    source = obj.optString("source", "external"),
                    filename = obj.optString("filename", ""),
                    format = obj.optString("format", "vtt"),
                    lang = obj.optString("lang", "id"),
                    label = obj.optString("label", "External Subtitle"),
                    downloadUrl = obj.optString("download_url", "")
                )
            )
        }
        return list
    }
}
