package id.miru.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.miru.app.data.AnimeCard
import id.miru.app.data.AnimeEdition
import id.miru.app.data.ApiClient
import id.miru.app.data.BookmarkItem
import id.miru.app.data.WatchHistoryItem
import id.miru.app.ui.components.detail.DetailEditionsSection
import id.miru.app.ui.components.detail.DetailEpisodeList
import id.miru.app.ui.components.detail.DetailHeroHeader
import id.miru.app.ui.components.detail.DetailSynopsisSection
import id.miru.app.ui.components.detail.DetailTopBar
import id.miru.app.ui.theme.DarkBackground
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    anime: AnimeCard,
    onBackClick: () -> Unit,
    onPlayEpisodeClick: (AnimeCard, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentAnime by remember(anime) { mutableStateOf(anime) }
    var isBookmarked by remember { mutableStateOf(false) }
    var watchedHistory by remember { mutableStateOf<WatchHistoryItem?>(null) }
    var editions by remember(currentAnime.slug) { mutableStateOf<List<AnimeEdition>>(emptyList()) }
    var isSynopsisExpanded by remember(currentAnime.slug) { mutableStateOf(false) }
    var translatedSynopsis by remember(currentAnime.slug) { mutableStateOf<String?>(null) }
    var isTranslating by remember(currentAnime.slug) { mutableStateOf(false) }
    var showOriginalSynopsis by remember(currentAnime.slug) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    BackHandler { onBackClick() }

    LaunchedEffect(currentAnime.slug) {
        launch { ApiClient.getBookmarks().onSuccess { list -> isBookmarked = list.any { it.slug == currentAnime.slug } } }
        launch { ApiClient.getHistory().onSuccess { list -> watchedHistory = list.firstOrNull { it.slug == currentAnime.slug } } }
        launch {
            ApiClient.getAnimeEditions(currentAnime.id, currentAnime.slug).onSuccess { list ->
                editions = list
                if (currentAnime.id == 0L || currentAnime.episodes == null) {
                    list.firstOrNull { it.isCurrent }?.let { ed ->
                        currentAnime = currentAnime.copy(
                            id = if (currentAnime.id > 0L) currentAnime.id else ed.id, idMal = currentAnime.idMal ?: ed.idMal,
                            score = currentAnime.score ?: ed.score, episodes = ed.episodes ?: currentAnime.episodes,
                            episodesReleased = ed.episodesReleased ?: currentAnime.episodesReleased,
                            status = ed.status ?: currentAnime.status, seasonYear = ed.seasonYear ?: currentAnime.seasonYear
                        )
                        ApiClient.getHistory().onSuccess { hList -> watchedHistory = hList.firstOrNull { h -> h.slug == ed.slug } }
                    }
                }
            }
        }
        if (currentAnime.synopsis.isNullOrBlank()) {
            launch {
                val query = currentAnime.title.ifBlank { currentAnime.slug }
                ApiClient.searchAnime(query = query, page = 1, perPage = 5).onSuccess { results ->
                    val match = results.firstOrNull { it.id == currentAnime.id || it.slug == currentAnime.slug } ?: results.firstOrNull()
                    if (match != null && !match.synopsis.isNullOrBlank()) {
                        currentAnime = currentAnime.copy(
                            synopsis = match.synopsis, banner = currentAnime.banner ?: match.banner,
                            genres = currentAnime.genres.ifEmpty { match.genres },
                            id = if (currentAnime.id > 0L) currentAnime.id else match.id, idMal = currentAnime.idMal ?: match.idMal,
                            score = currentAnime.score ?: match.score, episodes = currentAnime.episodes ?: match.episodes,
                            episodesReleased = currentAnime.episodesReleased ?: match.episodesReleased,
                            status = currentAnime.status ?: match.status, seasonYear = currentAnime.seasonYear ?: match.seasonYear
                        )
                    }
                }
            }
        }
    }

    val totalEpisodes = currentAnime.actualEpisodeCount
    val lastWatchedEp = watchedHistory?.episode ?: 1
    val lastProgress = watchedHistory?.progress ?: 0
    val isLastEpFinished = lastProgress >= 85
    val resumeEpisode = when {
        totalEpisodes == 0 || watchedHistory == null -> 1
        isLastEpFinished && lastWatchedEp < totalEpisodes -> lastWatchedEp + 1
        else -> lastWatchedEp.coerceIn(1, totalEpisodes.coerceAtLeast(1))
    }
    val currentProgress = if (isLastEpFinished) 0 else lastProgress
    val isContinueWatch = watchedHistory != null && (resumeEpisode > 1 || currentProgress > 0)
    val rawSynopsis = currentAnime.cleanSynopsis ?: "Belum ada sinopsis resmi untuk anime ini."
    val currentSynopsis = if (showOriginalSynopsis) rawSynopsis else (translatedSynopsis ?: rawSynopsis)

    val scrollOffset by remember {
        derivedStateOf { if (listState.firstVisibleItemIndex > 0) 1000f else listState.firstVisibleItemScrollOffset.toFloat() }
    }
    val parallaxOffset = (scrollOffset * 0.45f).coerceAtMost(160f)

    val toggleBookmark: () -> Unit = {
        val willBookmark = !isBookmarked
        isBookmarked = willBookmark
        scope.launch {
            if (willBookmark) ApiClient.addBookmark(BookmarkItem(currentAnime.slug, currentAnime.title, currentAnime.poster, currentAnime.status ?: "ANIME"))
            else ApiClient.removeBookmark(currentAnime.slug)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                DetailHeroHeader(
                    anime = currentAnime,
                    resumeEpisode = resumeEpisode,
                    isUpcoming = currentAnime.isNotYetReleased,
                    onPlayClick = { ep -> onPlayEpisodeClick(currentAnime, ep) },
                    parallaxOffset = parallaxOffset,
                    isContinueWatch = isContinueWatch
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
                DetailSynopsisSection(
                    synopsis = currentSynopsis,
                    isExpanded = isSynopsisExpanded,
                    onToggleExpand = { isSynopsisExpanded = !isSynopsisExpanded },
                    isTranslating = isTranslating,
                    hasTranslation = translatedSynopsis != null,
                    showingOriginal = showOriginalSynopsis,
                    onTranslateClick = {
                        if (currentAnime.cleanSynopsis.isNullOrBlank()) return@DetailSynopsisSection
                        if (translatedSynopsis != null) showOriginalSynopsis = !showOriginalSynopsis
                        else if (!isTranslating) {
                            isTranslating = true
                            scope.launch {
                                ApiClient.translateText(rawSynopsis).onSuccess { tr -> translatedSynopsis = tr; showOriginalSynopsis = false }
                                isTranslating = false
                            }
                        }
                    }
                )
            }

            if (editions.size > 1) {
                item { DetailEditionsSection(editions = editions, onEditionClick = { ed -> currentAnime = ed.toAnimeCard() }) }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                DetailEpisodeList(
                    totalEpisodes = totalEpisodes,
                    posterUrl = currentAnime.poster,
                    resumeEpisode = resumeEpisode,
                    currentProgress = currentProgress,
                    isUpcoming = currentAnime.isNotYetReleased,
                    onEpisodeClick = { ep -> onPlayEpisodeClick(currentAnime, ep) }
                )
            }
        }

        DetailTopBar(
            anime = currentAnime,
            scrollOffset = scrollOffset,
            resumeEpisode = resumeEpisode,
            isBookmarked = isBookmarked,
            onBackClick = onBackClick,
            onToggleBookmark = toggleBookmark,
            onPlayEpisodeClick = { ep -> onPlayEpisodeClick(currentAnime, ep) }
        )
    }
}
