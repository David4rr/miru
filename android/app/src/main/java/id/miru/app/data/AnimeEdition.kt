package id.miru.app.data

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
