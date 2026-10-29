package id.miru.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "http://127.0.0.1:8767"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun getHomeCatalog(): Result<HomeCatalog> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/api/home")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.message}")
                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val data = json.getJSONObject("data")

                val heroList = parseAnimeCards(data.optJSONArray("hero"))
                val continueList = parseWatchHistory(data.optJSONArray("continue_watching"))
                val airingToday = parseScheduleItems(data.optJSONArray("airing_today"))
                val topAiring = parseAnimeCards(data.optJSONArray("top_airing"))
                val seasonal = parseAnimeCards(data.optJSONArray("seasonal"))
                val topRated = parseAnimeCards(data.optJSONArray("top_rated"))
                val upcoming = parseAnimeCards(data.optJSONArray("upcoming"))
                val allTimePopular = parseAnimeCards(data.optJSONArray("all_time_popular"))

                HomeCatalog(
                    hero = heroList,
                    continueWatching = continueList,
                    airingToday = airingToday,
                    topAiring = topAiring,
                    seasonal = seasonal,
                    topRated = topRated,
                    upcoming = upcoming,
                    allTimePopular = allTimePopular
                )
            }
        }
    }

    suspend fun searchAnime(query: String, page: Int = 1, perPage: Int = 20): Result<List<AnimeCard>> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("$BASE_URL/api/search?q=$encodedQuery&page=$page&per_page=$perPage")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.message}")
                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val data = json.optJSONArray("data")
                parseAnimeCards(data)
            }
        }
    }

    suspend fun resolveStream(
        slug: String,
        ep: Int,
        mode: String = "sub",
        malId: Long? = null,
        title: String? = null
    ): Result<ResolvedStream> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedSlug = java.net.URLEncoder.encode(slug, "UTF-8")
            val malQuery = if (malId != null && malId > 0) "&mal_id=$malId" else ""
            val titleQuery = if (!title.isNullOrBlank()) "&title=${java.net.URLEncoder.encode(title, "UTF-8")}" else ""
            val request = Request.Builder()
                .url("$BASE_URL/api/stream/resolve?slug=$encodedSlug&ep=$ep&mode=$mode$malQuery$titleQuery")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = runCatching { JSONObject(bodyStr).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }
                    throw Exception(detail ?: "HTTP ${resp.code}: ${resp.message}")
                }
                val json = JSONObject(bodyStr)
                val data = json.getJSONObject("data")

                val subArray = data.optJSONArray("subtitles") ?: JSONArray()
                val subtitles = mutableListOf<SubtitleTrack>()
                for (i in 0 until subArray.length()) {
                    val sObj = subArray.getJSONObject(i)
                    val rawUrl = sObj.optString("url")
                    val resolvedSubUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                        rawUrl
                    } else {
                        "$BASE_URL$rawUrl"
                    }
                    subtitles.add(
                        SubtitleTrack(
                            label = sObj.optString("label"),
                            lang = sObj.optString("lang"),
                            url = resolvedSubUrl,
                            default = sObj.optBoolean("default", false)
                        )
                    )
                }

                val introObj = data.optJSONObject("intro")
                val outroObj = data.optJSONObject("outro")

                ResolvedStream(
                    slug = data.optString("slug"),
                    episode = data.optInt("episode"),
                    mode = data.optString("mode"),
                    master = data.optString("master"),
                    referer = data.optString("referer"),
                    server = data.optString("server"),
                    subtitles = subtitles,
                    intro = SkipInterval(
                        start = introObj?.optDouble("start") ?: 0.0,
                        end = introObj?.optDouble("end") ?: 0.0
                    ),
                    outro = SkipInterval(
                        start = outroObj?.optDouble("start") ?: 0.0,
                        end = outroObj?.optDouble("end") ?: 0.0
                    ),
                    proxiedMasterUrl = run {
                        val raw = data.optString("proxied_master_url")
                        if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "$BASE_URL$raw"
                    }
                )
            }
        }
    }

    suspend fun getBookmarks(): Result<List<BookmarkItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$BASE_URL/api/bookmarks").get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string().orEmpty())
                val arr = json.optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<BookmarkItem>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    list.add(
                        BookmarkItem(
                            slug = item.optString("slug"),
                            title = item.optString("title"),
                            poster = item.optString("poster"),
                            type = item.optString("type"),
                            sub = item.optString("sub"),
                            eps = item.optString("eps"),
                            score = item.optDouble("score", 0.0),
                            createdAt = item.optString("created_at")
                        )
                    )
                }
                list
            }
        }
    }

    suspend fun addBookmark(item: BookmarkItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject().apply {
                put("slug", item.slug)
                put("title", item.title)
                put("poster", item.poster)
                put("type", item.type)
                put("sub", item.sub)
                put("eps", item.eps)
                put("score", item.score)
            }
            val request = Request.Builder()
                .url("$BASE_URL/api/bookmarks")
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(request).execute().close()
        }
    }

    suspend fun removeBookmark(slug: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/api/bookmarks/$slug")
                .delete()
                .build()

            client.newCall(request).execute().close()
        }
    }

    suspend fun getHistory(): Result<List<WatchHistoryItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$BASE_URL/api/history").get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string().orEmpty())
                parseWatchHistory(json.optJSONArray("data"))
            }
        }
    }

    suspend fun addHistory(item: WatchHistoryItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject().apply {
                put("slug", item.slug)
                put("title", item.title)
                put("episode", item.episode)
                put("mode", item.mode)
                put("progress", item.progress)
                put("poster", item.poster)
                put("type", item.type)
            }
            val request = Request.Builder()
                .url("$BASE_URL/api/history")
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(request).execute().close()
        }
    }
    suspend fun deleteHistory(slug: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/api/history/$slug")
                .delete()
                .build()
            client.newCall(request).execute().close()
        }
    }

    suspend fun clearHistory(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/api/history")
                .delete()
                .build()
            client.newCall(request).execute().close()
        }
    }


    suspend fun saveProgress(
        slug: String,
        episode: Int,
        progress: Int,
        duration: Int,
        title: String? = null,
        poster: String? = null,
        type: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject().apply {
                put("slug", slug)
                put("episode", episode)
                put("progress", progress)
                put("duration", duration)
                title?.let { put("title", it) }
                poster?.let { put("poster", it) }
                type?.let { put("type", it) }
            }
            val request = Request.Builder()
                .url("$BASE_URL/api/progress")
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(request).execute().close()
        }
    }
    suspend fun getProgress(slug: String, episode: Int): Result<WatchProgressItem?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/api/progress?slug=$slug&ep=$episode")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val json = JSONObject(resp.body?.string().orEmpty())
                val data = json.optJSONObject("data") ?: return@runCatching null
                WatchProgressItem(
                    slug = data.optString("slug"),
                    episode = data.optInt("episode"),
                    progress = data.optInt("progress"),
                    duration = data.optInt("duration")
                )
            }
        }
    }
    suspend fun translateText(text: String, sl: String = "auto", tl: String = "id"): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedText = java.net.URLEncoder.encode(text, "UTF-8")
            val request = Request.Builder()
                .url("$BASE_URL/api/translate?text=$encodedText&sl=$sl&tl=$tl")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string().orEmpty())
                json.optString("data", text)
            }
        }
    }

    suspend fun getSkipTimes(malId: Long, episode: Int, length: Double = 1440.0): Result<Pair<SkipInterval, SkipInterval>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/api/player/skip_times?mal_id=$malId&ep=$episode&length=$length")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching Pair(SkipInterval(), SkipInterval())
                val json = JSONObject(resp.body?.string().orEmpty())
                val data = json.optJSONObject("data") ?: return@runCatching Pair(SkipInterval(), SkipInterval())
                val opObj = data.optJSONObject("op")
                val edObj = data.optJSONObject("ed")
                val intro = SkipInterval(opObj?.optDouble("start") ?: 0.0, opObj?.optDouble("end") ?: 0.0)
                val outro = SkipInterval(edObj?.optDouble("start") ?: 0.0, edObj?.optDouble("end") ?: 0.0)
                Pair(intro, outro)
            }
        }
    }
    suspend fun getWeeklySchedule(): Result<WeeklySchedule> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/api/schedule")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: ${resp.message}")
                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val data = json.getJSONObject("data")
                val daysArr = data.optJSONArray("days")
                val daysList = mutableListOf<DaySchedule>()

                if (daysArr != null) {
                    for (i in 0 until daysArr.length()) {
                        val dObj = daysArr.optJSONObject(i) ?: continue
                        val itemsArr = dObj.optJSONArray("items")
                        val items = parseScheduleItems(itemsArr)
                        daysList.add(
                            DaySchedule(
                                dayName = dObj.optString("day_name"),
                                dayKey = dObj.optString("day_key"),
                                dateFormatted = dObj.optString("date_formatted"),
                                isToday = dObj.optBoolean("is_today", false),
                                items = items
                            )
                        )
                    }
                }

                WeeklySchedule(days = daysList)
            }
        }
    }

    suspend fun getAnimeEditions(animeId: Long, slug: String): Result<List<AnimeEdition>> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedSlug = java.net.URLEncoder.encode(slug, "UTF-8")
            val url = if (animeId > 0) {
                "$BASE_URL/api/anime/editions?id=$animeId&slug=$encodedSlug"
            } else {
                "$BASE_URL/api/anime/editions?slug=$encodedSlug"
            }
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val json = JSONObject(resp.body?.string().orEmpty())
                val arr = json.optJSONArray("data") ?: return@runCatching emptyList()
                val list = mutableListOf<AnimeEdition>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(
                        AnimeEdition(
                            id = obj.optLong("id"),
                            idMal = if (obj.has("id_mal") && !obj.isNull("id_mal")) obj.optLong("id_mal") else null,
                            slug = obj.optString("slug"),
                            title = obj.optString("title"),
                            titleEnglish = if (obj.has("title_english") && !obj.isNull("title_english")) obj.optString("title_english") else null,
                            format = obj.optString("format", "TV"),
                            status = if (obj.has("status") && !obj.isNull("status")) obj.optString("status") else null,
                            seasonYear = if (obj.has("season_year") && !obj.isNull("season_year")) obj.optInt("season_year") else null,
                            episodes = if (obj.has("episodes") && !obj.isNull("episodes")) obj.optInt("episodes") else null,
                            episodesReleased = if (obj.has("episodes_released") && !obj.isNull("episodes_released")) obj.optInt("episodes_released") else null,
                            poster = obj.optString("poster"),
                            score = if (obj.has("score") && !obj.isNull("score")) obj.optDouble("score") else null,
                            editionLabel = obj.optString("edition_label", "S1"),
                            isCurrent = obj.optBoolean("is_current", false),
                            dateOrder = obj.optLong("date_order", 0L)
                        )
                    )
                }
                list
            }
        }
    }

    private fun parseScheduleItems(array: JSONArray?): List<ScheduleItem> {
        if (array == null) return emptyList()
        val list = mutableListOf<ScheduleItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val animeObj = obj.optJSONObject("anime") ?: continue
            val animeCardList = parseAnimeCards(JSONArray().apply { put(animeObj) })
            if (animeCardList.isEmpty()) continue
            list.add(
                ScheduleItem(
                    id = obj.optLong("id"),
                    episode = obj.optInt("episode"),
                    airingAt = obj.optLong("airing_at"),
                    anime = animeCardList[0]
                )
            )
        }
        return list
    }

    private fun parseAnimeCards(array: JSONArray?): List<AnimeCard> {
        if (array == null) return emptyList()
        val list = mutableListOf<AnimeCard>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val genresArr = obj.optJSONArray("genres")
            val genres = mutableListOf<String>()
            if (genresArr != null) {
                for (g in 0 until genresArr.length()) {
                    genres.add(genresArr.optString(g))
                }
            }

            list.add(
                AnimeCard(
                    id = obj.optLong("id"),
                    idMal = if (obj.has("id_mal") && !obj.isNull("id_mal")) obj.optLong("id_mal") else null,
                    slug = obj.optString("slug"),
                    title = obj.optString("title"),
                    titleEnglish = if (obj.has("title_english") && !obj.isNull("title_english")) obj.optString("title_english") else null,
                    poster = obj.optString("poster"),
                    banner = if (obj.has("banner") && !obj.isNull("banner")) obj.optString("banner") else null,
                    score = if (obj.has("score") && !obj.isNull("score")) obj.optDouble("score") else null,
                    episodes = if (obj.has("episodes") && !obj.isNull("episodes")) obj.optInt("episodes") else null,
                    episodesReleased = if (obj.has("episodes_released") && !obj.isNull("episodes_released")) obj.optInt("episodes_released") else null,
                    status = if (obj.has("status") && !obj.isNull("status")) obj.optString("status") else null,
                    seasonYear = if (obj.has("season_year") && !obj.isNull("season_year")) obj.optInt("season_year") else null,
                    genres = genres,
                    synopsis = if (obj.has("synopsis") && !obj.isNull("synopsis")) obj.optString("synopsis") else null
                )
            )
        }
        return list
    }

    private fun parseWatchHistory(array: JSONArray?): List<WatchHistoryItem> {
        if (array == null) return emptyList()
        val list = mutableListOf<WatchHistoryItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(
                WatchHistoryItem(
                    id = if (obj.has("id") && !obj.isNull("id")) obj.optLong("id") else null,
                    slug = obj.optString("slug"),
                    title = obj.optString("title"),
                    episode = obj.optInt("episode"),
                    mode = obj.optString("mode", "sub"),
                    progress = obj.optInt("progress", 0),
                    poster = obj.optString("poster"),
                    type = obj.optString("type"),
                    playedAt = if (obj.has("played_at")) obj.optString("played_at") else null
                )
            )
        }
        return list
    }
}
