package id.tatap.app.data

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

    val displayEpisodes: String
        get() = when {
            episodes != null && episodes > 0 -> "$episodes Eps"
            episodesReleased != null && episodesReleased > 0 -> "Ep $episodesReleased"
            else -> "Ongoing"
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
                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                sdf.format(date) + " WIB"
            } catch (e: Exception) {
                "Ep $episode"
            }
        }
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
