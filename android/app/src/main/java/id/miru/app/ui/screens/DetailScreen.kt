package id.miru.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.miru.app.data.AnimeCard
import id.miru.app.data.ApiClient
import id.miru.app.data.BookmarkItem
import id.miru.app.data.WatchHistoryItem
import id.miru.app.ui.components.detail.DetailEpisodeList
import id.miru.app.ui.components.detail.DetailHeroHeader
import id.miru.app.ui.components.detail.DetailSynopsisSection
import id.miru.app.ui.theme.DarkBackground
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    anime: AnimeCard,
    onBackClick: () -> Unit,
    onPlayEpisodeClick: (AnimeCard, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isBookmarked by remember { mutableStateOf(false) }
    var watchedHistory by remember { mutableStateOf<WatchHistoryItem?>(null) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }
    var translatedSynopsis by remember { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    var showOriginalSynopsis by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(anime.slug) {
        ApiClient.getBookmarks().onSuccess { list ->
            isBookmarked = list.any { it.slug == anime.slug }
        }
        ApiClient.getHistory().onSuccess { list ->
            watchedHistory = list.firstOrNull { it.slug == anime.slug }
        }
    }

    val totalEpisodes = if (anime.isNotYetReleased) 0 else (anime.episodes ?: 12)
    val lastWatchedEp = watchedHistory?.episode ?: 1
    val lastProgress = watchedHistory?.progress ?: 0
    val isLastEpFinished = lastProgress >= 85
    val resumeEpisode = when {
        watchedHistory == null -> 1
        isLastEpFinished && (totalEpisodes == 0 || lastWatchedEp < totalEpisodes) -> lastWatchedEp + 1
        else -> lastWatchedEp
    }
    val currentProgress = if (isLastEpFinished) 0 else lastProgress

    val rawSynopsis = anime.cleanSynopsis
    val currentSynopsis = if (showOriginalSynopsis || translatedSynopsis == null) rawSynopsis else translatedSynopsis!!

    Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                DetailHeroHeader(
                    anime = anime,
                    isBookmarked = isBookmarked,
                    onToggleBookmark = {
                        val willBeBookmarked = !isBookmarked
                        isBookmarked = willBeBookmarked
                        scope.launch {
                            if (willBeBookmarked) {
                                ApiClient.addBookmark(BookmarkItem(slug = anime.slug, title = anime.title, poster = anime.poster, type = anime.status ?: "ANIME"))
                            } else {
                                ApiClient.deleteBookmark(anime.slug)
                            }
                        }
                    },
                    resumeEpisode = resumeEpisode,
                    isUpcoming = anime.isNotYetReleased,
                    onPlayClick = { ep -> onPlayEpisodeClick(anime, ep) }
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
                        if (translatedSynopsis != null) {
                            showOriginalSynopsis = !showOriginalSynopsis
                        } else if (!isTranslating) {
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

            item {
                Spacer(modifier = Modifier.height(24.dp))
                DetailEpisodeList(
                    totalEpisodes = totalEpisodes,
                    posterUrl = anime.poster,
                    resumeEpisode = resumeEpisode,
                    currentProgress = currentProgress,
                    isUpcoming = anime.isNotYetReleased,
                    onEpisodeClick = { ep -> onPlayEpisodeClick(anime, ep) }
                )
            }
        }

        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .size(40.dp)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
        }
    }
}
