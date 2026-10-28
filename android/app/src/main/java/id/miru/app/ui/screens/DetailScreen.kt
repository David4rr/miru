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
    var isSynopsisExpanded by remember { mutableStateOf(false) }
    var translatedSynopsis by remember { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    var showOriginalSynopsis by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    BackHandler(enabled = currentAnime != anime) { currentAnime = anime }

    LaunchedEffect(currentAnime.slug) {
        ApiClient.getBookmarks().onSuccess { list -> isBookmarked = list.any { it.slug == currentAnime.slug } }
        ApiClient.getHistory().onSuccess { list -> watchedHistory = list.firstOrNull { it.slug == currentAnime.slug } }
        ApiClient.getAnimeEditions(currentAnime.id, currentAnime.slug).onSuccess { list ->
            editions = list
            if (currentAnime.id == 0L || currentAnime.episodes == null) {
                list.firstOrNull { it.isCurrent }?.let { currentAnime = it.toAnimeCard() }
            }
        }
    }

    val totalEpisodes = currentAnime.actualEpisodeCount
    val lastWatchedEp = watchedHistory?.episode ?: 1
    val lastProgress = watchedHistory?.progress ?: 0
    val isLastEpFinished = lastProgress >= 85
    val resumeEpisode = when {
        totalEpisodes == 0 -> 1
        watchedHistory == null -> 1
        isLastEpFinished && lastWatchedEp < totalEpisodes -> lastWatchedEp + 1
        else -> lastWatchedEp.coerceIn(1, totalEpisodes.coerceAtLeast(1))
    }
    val currentProgress = if (isLastEpFinished) 0 else lastProgress

    val rawSynopsis = currentAnime.cleanSynopsis ?: "Belum ada sinopsis resmi untuk anime ini."
    val currentSynopsis = if (showOriginalSynopsis) rawSynopsis else (translatedSynopsis ?: rawSynopsis)

    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1000f
            else listState.firstVisibleItemScrollOffset.toFloat()
        }
    }
    val parallaxOffset = (scrollOffset * 0.45f).coerceAtMost(160f)

    Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                DetailHeroHeader(
                    anime = currentAnime,
                    isBookmarked = isBookmarked,
                    onToggleBookmark = {
                        val willBookmark = !isBookmarked
                        isBookmarked = willBookmark
                        scope.launch {
                            if (willBookmark) ApiClient.addBookmark(BookmarkItem(slug = currentAnime.slug, title = currentAnime.title, poster = currentAnime.poster, type = currentAnime.status ?: "ANIME"))
                            else ApiClient.removeBookmark(currentAnime.slug)
                        }
                    },
                    resumeEpisode = resumeEpisode,
                    isUpcoming = currentAnime.isNotYetReleased,
                    onPlayClick = { ep -> onPlayEpisodeClick(currentAnime, ep) },
                    parallaxOffset = parallaxOffset
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
                        if (translatedSynopsis != null) showOriginalSynopsis = !showOriginalSynopsis
                        else if (!isTranslating) {
                            isTranslating = true
                            scope.launch {
                                ApiClient.translateText(rawSynopsis).onSuccess { tr ->
                                    translatedSynopsis = tr
                                    showOriginalSynopsis = false
                                }
                                isTranslating = false
                            }
                        }
                    }
                )
            }

            if (editions.size > 1) {
                item {
                    DetailEditionsSection(
                        editions = editions,
                        onEditionClick = { ed -> currentAnime = ed.toAnimeCard() }
                    )
                }
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
            onBackClick = { if (currentAnime != anime) currentAnime = anime else onBackClick() },
            onToggleBookmark = {
                val willBookmark = !isBookmarked
                isBookmarked = willBookmark
                scope.launch {
                    if (willBookmark) ApiClient.addBookmark(BookmarkItem(slug = currentAnime.slug, title = currentAnime.title, poster = currentAnime.poster, type = currentAnime.status ?: "ANIME"))
                    else ApiClient.removeBookmark(currentAnime.slug)
                }
            },
            onPlayEpisodeClick = { ep -> onPlayEpisodeClick(currentAnime, ep) }
        )
    }
}
