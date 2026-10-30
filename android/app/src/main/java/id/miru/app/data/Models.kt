package id.miru.app.data

data class AnimeCard(
    val id: Long,
    val idMal: Long? = null,
    val slug: String,
    val title: String,
    val titleEnglish: String? = null,
    val poster: String,
    val banner: String? = null,
    val score: Double? = null,
    val episodes: Int? = null,
    val episodesReleased: Int? = null,
    val status: String? = null,
    val seasonYear: Int? = null,
    val genres: List<String> = emptyList(),
    val synopsis: String? = null
) {
    val displayScore: String
        get() = score?.let { String.format("%.1f", it) } ?: "N/A"

    val isNotYetReleased: Boolean
        get() = status.equals("NOT_YET_RELEASED", ignoreCase = true)

    val isFinished: Boolean
        get() = status.equals("FINISHED", ignoreCase = true)

    val isReleasing: Boolean
        get() = status.equals("RELEASING", ignoreCase = true)

    val displayStatus: String
        get() = when {
            status.equals("NOT_YET_RELEASED", ignoreCase = true) -> "Belum Rilis"
            status.equals("RELEASING", ignoreCase = true) -> "Sedang Tayang"
            status.equals("FINISHED", ignoreCase = true) -> "Tamat"
            status.equals("CANCELLED", ignoreCase = true) -> "Dibatalkan"
            status.equals("HIATUS", ignoreCase = true) -> "Hiatus"
            status != null -> status
            else -> "Sedang Tayang"
        }

    val actualEpisodeCount: Int
        get() {
            if (isNotYetReleased) return 0
            if (isReleasing) {
                if (episodesReleased != null) {
                    if (episodesReleased <= 0) return 0
                    return if (episodes != null && episodes > 0) episodesReleased.coerceAtMost(episodes) else episodesReleased
                }
                if (episodes != null && episodes > 0) return episodes
                return 1
            }
            if (isFinished) {
                return episodes ?: episodesReleased ?: 1
            }
            if (episodesReleased != null) {
                if (episodesReleased <= 0) return 0
                return episodesReleased
            }
            if (episodes != null && episodes > 0) return episodes
            return 1
        }

    val displayEpisodes: String
        get() = when {
            isNotYetReleased || (isReleasing && episodesReleased != null && episodesReleased <= 0) -> "Segera Tayang"
            isReleasing && episodesReleased != null && episodesReleased > 0 -> {
                if (episodes != null && episodes > 0) "Ep $episodesReleased / $episodes"
                else "Ep $episodesReleased"
            }
            episodes != null && episodes > 0 -> "$episodes Eps"
            episodesReleased != null && episodesReleased > 0 -> "Ep $episodesReleased"
            isFinished -> "Tamat"
            else -> "Sedang Tayang"
        }

    val displayGenres: List<String>
        get() = genres.map { translateGenre(it) }

    val cleanSynopsis: String?
        get() = synopsis?.let { raw ->
            val withoutBr = raw.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
            withoutBr.replace(Regex("<[^>]+>"), "").trim()
        }

    companion object {
        fun translateGenre(genre: String): String {
            return when (genre.trim().lowercase()) {
                "action" -> "Aksi"
                "adventure" -> "Petualangan"
                "comedy" -> "Komedi"
                "drama" -> "Drama"
                "fantasy" -> "Fantasi"
                "horror" -> "Horor"
                "mystery" -> "Misteri"
                "psychological" -> "Psikologis"
                "romance" -> "Romantis"
                "sci-fi" -> "Fiksi Ilmiah"
                "slice of life" -> "Seputar Kehidupan"
                "sports" -> "Olahraga"
                "supernatural" -> "Supranatural"
                "thriller" -> "Cerita Seru"
                "mecha" -> "Mecha"
                "music" -> "Musik"
                else -> genre
            }
        }
    }
}

data class ScheduleItem(
    val id: Long,
    val episode: Int,
    val airingAt: Long,
    val anime: AnimeCard
) {
    val formattedTime: String
        get() {
            return try {
                val date = java.util.Date(airingAt * 1000)
                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Jakarta")
                sdf.format(date) + " WIB"
            } catch (e: Exception) {
                "Ep $episode"
            }
        }

    val isAired: Boolean
        get() = (System.currentTimeMillis() / 1000) >= airingAt
}

data class DaySchedule(
    val dayName: String,
    val dayKey: String,
    val dateFormatted: String,
    val isToday: Boolean,
    val items: List<ScheduleItem> = emptyList()
)

data class WeeklySchedule(
    val days: List<DaySchedule> = emptyList()
)

data class HomeCatalog(
    val hero: List<AnimeCard> = emptyList(),
    val continueWatching: List<WatchHistoryItem> = emptyList(),
    val airingToday: List<ScheduleItem> = emptyList(),
    val topAiring: List<AnimeCard> = emptyList(),
    val seasonal: List<AnimeCard> = emptyList(),
    val topRated: List<AnimeCard> = emptyList(),
    val upcoming: List<AnimeCard> = emptyList(),
    val allTimePopular: List<AnimeCard> = emptyList()
)

data class WatchHistoryItem(
    val id: Long? = null,
    val slug: String,
    val title: String,
    val episode: Int,
    val mode: String = "sub",
    val progress: Int = 0,
    val poster: String = "",
    val type: String = "",
    val playedAt: String? = null
)

data class WatchProgressItem(
    val slug: String,
    val episode: Int,
    val progress: Int,
    val duration: Int,
    val updatedAt: String? = null
)

data class BookmarkItem(
    val slug: String,
    val title: String,
    val poster: String = "",
    val type: String = "",
    val sub: String = "",
    val eps: String = "",
    val score: Double = 0.0,
    val createdAt: String? = null
)

data class SubtitleTrack(
    val label: String,
    val lang: String = "",
    val url: String,
    val default: Boolean = false
)

data class SkipInterval(
    val start: Double = 0.0,
    val end: Double = 0.0
)

data class ResolvedStream(
    val slug: String,
    val episode: Int,
    val mode: String,
    val master: String,
    val referer: String,
    val server: String,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val intro: SkipInterval = SkipInterval(),
    val outro: SkipInterval = SkipInterval(),
    val proxiedMasterUrl: String
)

data class AppRelease(
    val tagName: String,
    val versionName: String,
    val title: String,
    val body: String,
    val publishedAt: String,
    val downloadUrl: String?,
    val apkSize: Long,
    val isNewer: Boolean = false
) {
    val displaySize: String
        get() = if (apkSize > 0) {
            String.format(java.util.Locale.US, "%.1f MB", apkSize / (1024.0 * 1024.0))
        } else {
            ""
        }
}

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: java.io.File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

data class AnimeEdition(
    val id: Long,
    val idMal: Long? = null,
    val slug: String,
    val title: String,
    val titleEnglish: String? = null,
    val format: String = "TV",
    val status: String? = null,
    val seasonYear: Int? = null,
    val episodes: Int? = null,
    val episodesReleased: Int? = null,
    val poster: String = "",
    val score: Double? = null,
    val editionLabel: String = "S1",
    val isCurrent: Boolean = false,
    val dateOrder: Long = 0L
) {
    val displayTitle: String
        get() = titleEnglish?.takeIf { it.isNotBlank() } ?: title

    val isNotYetReleased: Boolean
        get() = status.equals("NOT_YET_RELEASED", ignoreCase = true)

    val isFinished: Boolean
        get() = status.equals("FINISHED", ignoreCase = true)

    val isReleasing: Boolean
        get() = status.equals("RELEASING", ignoreCase = true)

    val actualEpisodeCount: Int
        get() {
            if (isNotYetReleased) return 0
            if (isReleasing) {
                if (episodesReleased != null) {
                    if (episodesReleased <= 0) return 0
                    return if (episodes != null && episodes > 0) episodesReleased.coerceAtMost(episodes) else episodesReleased
                }
                if (episodes != null && episodes > 0) return episodes
                return 1
            }
            if (isFinished) {
                return episodes ?: episodesReleased ?: 1
            }
            if (episodesReleased != null) {
                if (episodesReleased <= 0) return 0
                return episodesReleased
            }
            if (episodes != null && episodes > 0) return episodes
            return 1
        }

    val displaySubtitle: String
        get() {
            val parts = mutableListOf<String>()
            seasonYear?.let { parts.add(it.toString()) }
            if (isNotYetReleased || (isReleasing && episodesReleased != null && episodesReleased <= 0)) {
                parts.add("Segera")
            } else if (isReleasing && episodesReleased != null && episodesReleased > 0) {
                if (episodes != null && episodes > 0) parts.add("Ep $episodesReleased / $episodes")
                else parts.add("Ep $episodesReleased")
            } else if (episodes != null && episodes > 0) {
                parts.add("$episodes Ep")
            } else if (episodesReleased != null && episodesReleased > 0) {
                parts.add("Ep $episodesReleased")
            }
            return parts.joinToString(" • ")
        }

    fun toAnimeCard(): AnimeCard {
        return AnimeCard(
            id = id,
            idMal = idMal,
            slug = slug,
            title = title,
            titleEnglish = titleEnglish,
            poster = poster,
            score = score,
            episodes = episodes,
            episodesReleased = episodesReleased,
            status = status,
            seasonYear = seasonYear
        )
    }
}
