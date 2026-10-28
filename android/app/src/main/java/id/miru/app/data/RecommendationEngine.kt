package id.miru.app.data

/**
 * Smart recommendation engine that computes personalized anime suggestions
 * based on the user's watch history, active playback, and genre affinities.
 * Guarantees < 200 LOC and zero force-unwraps (!!).
 */
object RecommendationEngine {

    data class RecommendationResult(
        val title: String,
        val subtitle: String?,
        val badge: String?,
        val items: List<AnimeCard>
    )

    fun generateRecommendations(
        catalog: HomeCatalog,
        history: List<WatchHistoryItem>
    ): RecommendationResult {
        // Collect all distinct anime candidates from catalog sections
        val candidatePool = (catalog.topAiring + catalog.seasonal + catalog.topRated + catalog.allTimePopular + catalog.hero)
            .distinctBy { it.slug }

        // Gather slugs of anime the user has already watched or is continuing
        val watchedSlugs = (history.map { it.slug } + catalog.continueWatching.map { it.slug })
            .filter { it.isNotBlank() }
            .toSet()

        // Exclude already watched titles to suggest fresh content
        val unwatchedCandidates = candidatePool.filter { it.slug !in watchedSlugs }

        // Calculate genre affinity frequency from watched anime
        val watchedCards = candidatePool.filter { it.slug in watchedSlugs }
        val genreFrequency = mutableMapOf<String, Int>()
        for (card in watchedCards) {
            for (genre in card.genres) {
                genreFrequency[genre] = (genreFrequency[genre] ?: 0) + 1
            }
        }

        val topWatchedAnime = history.firstOrNull() ?: catalog.continueWatching.firstOrNull()

        if (genreFrequency.isNotEmpty() && topWatchedAnime != null) {
            val topGenre = genreFrequency.maxByOrNull { it.value }?.key
            val translatedTopGenre = topGenre?.let { AnimeCard.translateGenre(it) }

            // Score candidate anime: genre overlap bonus + community rating
            val scored = unwatchedCandidates.map { card ->
                var score = card.score ?: 7.0
                val matchingGenres = card.genres.count { it in genreFrequency }
                score += matchingGenres * 2.5
                if (topGenre != null && topGenre in card.genres) {
                    score += 4.0
                }
                card to score
            }.sortedByDescending { it.second }
             .map { it.first }

            val recommendedList = scored.take(12)
            if (recommendedList.isNotEmpty()) {
                val subtitle = if (translatedTopGenre != null) {
                    "Berdasarkan minatmu di genre $translatedTopGenre"
                } else {
                    "Dipilih khusus berdasarkan tontonanmu"
                }

                return RecommendationResult(
                    title = "Rekomendasi Pintar",
                    subtitle = subtitle,
                    badge = "Untukmu",
                    items = recommendedList
                )
            }
        }

        // Curated high-rated fallback when watch history is fresh or empty
        val topPicks = unwatchedCandidates.ifEmpty { candidatePool }
            .sortedByDescending { it.score ?: 0.0 }
            .take(10)

        return RecommendationResult(
            title = "Rekomendasi Untukmu",
            subtitle = "Anime rating tertinggi pilihan komunitas",
            badge = "Populer",
            items = topPicks
        )
    }
}
