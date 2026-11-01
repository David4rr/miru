package id.miru.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

suspend fun ApiClient.getCatalogFilters(): Result<CatalogFilterOptions> = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder().url("$BASE_URL/api/catalog/filters").get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.message}")
            val json = JSONObject(resp.body?.string().orEmpty())
            val data = json.getJSONObject("data")

            val genres = mutableListOf<String>()
            val gArr = data.optJSONArray("genres")
            if (gArr != null) {
                for (i in 0 until gArr.length()) genres.add(gArr.optString(i))
            }

            val seasons = mutableListOf<String>()
            val sArr = data.optJSONArray("seasons")
            if (sArr != null) {
                for (i in 0 until sArr.length()) seasons.add(sArr.optString(i))
            }

            val formats = mutableListOf<String>()
            val fArr = data.optJSONArray("formats")
            if (fArr != null) {
                for (i in 0 until fArr.length()) formats.add(fArr.optString(i))
            }

            val statuses = mutableListOf<String>()
            val stArr = data.optJSONArray("statuses")
            if (stArr != null) {
                for (i in 0 until stArr.length()) statuses.add(stArr.optString(i))
            }

            val years = mutableListOf<Int>()
            val yArr = data.optJSONArray("years")
            if (yArr != null) {
                for (i in 0 until yArr.length()) years.add(yArr.optInt(i))
            }

            val sortOptions = mutableListOf<CatalogSortOption>()
            val sortArr = data.optJSONArray("sort_options")
            if (sortArr != null) {
                for (i in 0 until sortArr.length()) {
                    val sObj = sortArr.optJSONObject(i) ?: continue
                    sortOptions.add(CatalogSortOption(sObj.optString("label"), sObj.optString("value")))
                }
            }

            CatalogFilterOptions(genres, seasons, formats, statuses, years, sortOptions)
        }
    }
}

suspend fun ApiClient.browseCatalog(params: CatalogBrowseParams): Result<List<AnimeCard>> = withContext(Dispatchers.IO) {
    runCatching {
        val queryParts = mutableListOf<String>()
        params.genre?.takeIf { it.isNotBlank() }?.let { queryParts.add("genre=${URLEncoder.encode(it, "UTF-8")}") }
        params.season?.takeIf { it.isNotBlank() }?.let { queryParts.add("season=${URLEncoder.encode(it, "UTF-8")}") }
        params.year?.takeIf { it > 1900 }?.let { queryParts.add("year=$it") }
        params.format?.takeIf { it.isNotBlank() }?.let { queryParts.add("format=${URLEncoder.encode(it, "UTF-8")}") }
        params.status?.takeIf { it.isNotBlank() }?.let { queryParts.add("status=${URLEncoder.encode(it, "UTF-8")}") }
        params.sort?.takeIf { it.isNotBlank() }?.let { queryParts.add("sort=${URLEncoder.encode(it, "UTF-8")}") }
        queryParts.add("page=${params.page}")
        queryParts.add("per_page=${params.perPage}")

        val url = "$BASE_URL/api/catalog/browse?${queryParts.joinToString("&")}"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.message}")
            val json = JSONObject(resp.body?.string().orEmpty())
            parseAnimeCards(json.optJSONArray("data"))
        }
    }
}

suspend fun ApiClient.getSafeMode(): Result<Boolean> = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder().url("$BASE_URL/api/settings/safemode").get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching true
            val json = JSONObject(resp.body?.string().orEmpty())
            json.optBoolean("safe_mode", true)
        }
    }
}

suspend fun ApiClient.setSafeMode(enabled: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
    runCatching {
        val json = JSONObject().put("safe_mode", enabled)
        val request = Request.Builder()
            .url("$BASE_URL/api/settings/safemode")
            .post(json.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val resJson = JSONObject(resp.body?.string().orEmpty())
            resJson.optBoolean("safe_mode", enabled)
        }
    }
}

suspend fun ApiClient.getSetting(key: String, defaultValue: String = ""): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder().url("$BASE_URL/api/settings/$key").get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching defaultValue
            val json = JSONObject(resp.body?.string().orEmpty())
            if (json.has("value") && !json.isNull("value")) json.optString("value", defaultValue) else defaultValue
        }
    }
}

suspend fun ApiClient.saveSetting(key: String, value: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val json = JSONObject().put("value", value)
        val request = Request.Builder()
            .url("$BASE_URL/api/settings/$key")
            .post(json.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().close()
    }
}

suspend fun ApiClient.execTerminalCommand(command: String): Result<TerminalResult> = withContext(Dispatchers.IO) {
    runCatching {
        val json = JSONObject().put("command", command)
        val request = Request.Builder()
            .url("$BASE_URL/api/terminal/exec")
            .post(json.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@runCatching TerminalResult(false, command, "HTTP ${resp.code}: $bodyStr")
            }
            val resJson = JSONObject(bodyStr)
            TerminalResult(
                success = resJson.optBoolean("success", true),
                command = resJson.optString("command", command),
                output = resJson.optString("output", "")
            )
        }
    }
}
