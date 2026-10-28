package id.miru.app.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.PlaybackParameters
import id.miru.app.data.AnimeCard
import id.miru.app.data.ResolvedStream
import id.miru.app.ui.components.player.PlayerControlsOverlay
import id.miru.app.ui.components.player.PlayerEpisodeSheet
import id.miru.app.ui.components.player.PlayerGestureOverlay
import id.miru.app.ui.components.player.PlayerLockOverlay
import id.miru.app.ui.components.player.PlayerVideoSurface
import id.miru.app.ui.components.player.cycleNextSpeed
import id.miru.app.ui.components.player.fetchResumePositionMs
import id.miru.app.ui.components.player.prepareMediaStream
import id.miru.app.ui.components.player.rememberExoPlayerWithLifecycle
import id.miru.app.ui.components.player.saveWatchProgressAsync
import id.miru.app.ui.theme.DarkBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    anime: AnimeCard,
    episode: Int,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentEpisode by remember { mutableIntStateOf(episode) }
    var isPlaying by remember { mutableStateOf(true) }; var isBuffering by remember { mutableStateOf(true) }
    var isLoadingStream by remember { mutableStateOf(true) }; var streamErrorMessage by remember { mutableStateOf<String?>(null) }
    var currentPosition by remember { mutableLongStateOf(0L) }; var duration by remember { mutableLongStateOf(0L) }
    var areControlsVisible by remember { mutableStateOf(true) }; var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isScreenLocked by remember { mutableStateOf(false) }; var showUnlockButton by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }; var showEpisodeSheet by remember { mutableStateOf(false) }
    var resolvedStream by remember { mutableStateOf<ResolvedStream?>(null) }; var seekRippleSide by remember { mutableStateOf<String?>(null) }
    var animeMetadata by remember(anime.slug) { mutableStateOf(anime) }
    LaunchedEffect(anime.slug) {
        if (anime.id == 0L || anime.episodes == null) {
            ApiClient.getAnimeEditions(anime.id, anime.slug).onSuccess { list ->
                list.firstOrNull { it.isCurrent }?.let { animeMetadata = it.toAnimeCard() }
            }
        }
    }
    val totalEpisodes = animeMetadata.actualEpisodeCount.coerceAtLeast(currentEpisode)

    fun ping() { lastInteraction = System.currentTimeMillis(); areControlsVisible = true }
    fun saveProgress(explicit: Int? = null) = saveWatchProgressAsync(anime.slug, currentEpisode, currentPosition, duration, explicit)

    val exoPlayer = rememberExoPlayerWithLifecycle(
        context = context, activity = context as? Activity, window = (context as? Activity)?.window,
        onIsPlayingChanged = { isPlaying = it }, onBufferingChanged = { isBuffering = it },
        onDurationChanged = { duration = it },
        onPlaybackEnded = { saveProgress(100); if (currentEpisode < totalEpisodes) currentEpisode += 1 },
        onDisposePlayer = { saveProgress() }
    )

    fun loadStream() {
        isLoadingStream = true; isBuffering = true; streamErrorMessage = null
        scope.launch {
            val resumeMs = fetchResumePositionMs(anime.slug, currentEpisode)
            prepareMediaStream(
                slug = animeMetadata.slug, episode = currentEpisode, malId = animeMetadata.idMal ?: anime.idMal,
                title = animeMetadata.title.ifBlank { anime.title }, exoPlayer = exoPlayer,
                resumePositionMs = resumeMs,
                onSuccess = { res -> resolvedStream = res; isLoadingStream = false; ping() },
                onFailure = { err -> isLoadingStream = false; isBuffering = false; streamErrorMessage = err }
            )
        }
    }

    LaunchedEffect(currentEpisode) { loadStream() }

    LaunchedEffect(areControlsVisible, isPlaying, isBuffering, isLoadingStream, lastInteraction) {
        if (areControlsVisible && isPlaying && !isBuffering && !isLoadingStream && streamErrorMessage == null) {
            delay(3500)
            areControlsVisible = false
        }
    }

    LaunchedEffect(exoPlayer, currentEpisode) {
        var tick = 0
        while (true) {
            try {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (duration <= 0 && exoPlayer.duration > 0) duration = exoPlayer.duration
                if (++tick % 10 == 0 && isPlaying) saveProgress()
            } catch (_: Exception) { break }
            delay(400)
        }
    }
    BackHandler { saveProgress(); exoPlayer.stop(); onBackClick() }

    Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        PlayerVideoSurface(
            exoPlayer = exoPlayer, isScreenLocked = isScreenLocked,
            onToggleControls = { if (areControlsVisible) areControlsVisible = false else ping() },
            onToggleUnlockButton = { showUnlockButton = !showUnlockButton },
            onDoubleTapSeek = { isLeft ->
                val delta = if (isLeft) -10_000 else 10_000
                exoPlayer.seekTo((exoPlayer.currentPosition + delta).coerceIn(0, duration))
                seekRippleSide = if (isLeft) "left" else "right"
                ping(); scope.launch { delay(600); seekRippleSide = null }
            }
        )
        PlayerGestureOverlay(false, 50, false, 50, seekRippleSide)


        PlayerLockOverlay(isScreenLocked, showUnlockButton, onUnlockClick = { isScreenLocked = false; showUnlockButton = false; ping() }, modifier = Modifier.align(Alignment.Center))

        if (!isScreenLocked) {
            PlayerControlsOverlay(
                isVisible = areControlsVisible, title = anime.title, episode = currentEpisode,
                onToggleLock = { isScreenLocked = true; areControlsVisible = false; showUnlockButton = true },
                onBackClick = { saveProgress(); exoPlayer.stop(); onBackClick() },
                isPlaying = isPlaying, isBuffering = isBuffering, isLoadingStream = isLoadingStream,
                hasNextEpisode = currentEpisode < totalEpisodes, errorMessage = streamErrorMessage,
                onRetryClick = { loadStream() },
                onPlayPauseClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play(); ping() },
                onRewind10Click = { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0)); ping() },
                onForward10Click = { exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(duration)); ping() },
                onNextEpisodeClick = { if (currentEpisode < totalEpisodes) currentEpisode += 1; ping() },
                currentPosition = currentPosition, duration = duration, playbackSpeed = playbackSpeed,
                onSeek = { target -> exoPlayer.seekTo(target); ping() },
                onCycleSpeed = {
                    playbackSpeed = cycleNextSpeed(playbackSpeed)
                    exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                    ping()
                },
                onOpenEpisodeSheet = { showEpisodeSheet = true }, onBackgroundClick = { areControlsVisible = false }
            )
        }

        if (showEpisodeSheet) {
            PlayerEpisodeSheet(totalEpisodes = totalEpisodes, currentEpisode = currentEpisode, onSelectEpisode = { ep -> currentEpisode = ep }, onDismissRequest = { showEpisodeSheet = false })
        }
    }
}
