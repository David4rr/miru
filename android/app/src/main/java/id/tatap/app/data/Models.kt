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

data class HomeCatalog(
    val hero: List<AnimeCard> = emptyList(),
    val continueWatching: List<WatchHistoryItem> = emptyList(),
    val topAiring: List<AnimeCard> = emptyList(),
    val seasonal: List<AnimeCard> = emptyList()
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
