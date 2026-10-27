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
import id.miru.app.ui.components.player.PlayerSkipOverlay
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
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }; var isAutoSkipEnabled by remember { mutableStateOf(true) }
    var autoSkipNotice by remember { mutableStateOf<String?>(null) }; var showEpisodeSheet by remember { mutableStateOf(false) }
    var resolvedStream by remember { mutableStateOf<ResolvedStream?>(null) }; var seekRippleSide by remember { mutableStateOf<String?>(null) }
    var hasSkippedIntro by remember(currentEpisode) { mutableStateOf(false) }; var hasSkippedOutro by remember(currentEpisode) { mutableStateOf(false) }
    val totalEpisodes = if (anime.isNotYetReleased) 0 else (anime.episodes ?: 12)

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
                slug = anime.slug, episode = currentEpisode, malId = anime.idMal, exoPlayer = exoPlayer,
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

    LaunchedEffect(exoPlayer, isAutoSkipEnabled, resolvedStream, currentEpisode) {
        var tick = 0
        while (true) {
            try {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (duration <= 0 && exoPlayer.duration > 0) duration = exoPlayer.duration
                val intro = resolvedStream?.intro
                val outro = resolvedStream?.outro
                val curSec = currentPosition / 1000.0
                if (intro != null && intro.end > intro.start && curSec < (intro.start - 5.0)) hasSkippedIntro = false
                if (outro != null && outro.end > outro.start && curSec < (outro.start - 5.0)) hasSkippedOutro = false
                if (isAutoSkipEnabled && isPlaying && !hasSkippedIntro && intro != null && intro.end > intro.start && curSec >= intro.start && curSec < (intro.end - 0.5)) {
                    exoPlayer.seekTo(((intro.end + 0.5) * 1000).toLong())
                    hasSkippedIntro = true; autoSkipNotice = "⏩ Lewati Pembuka (+${(intro.end - intro.start).toInt()}d)"
                }
                if (isAutoSkipEnabled && isPlaying && !hasSkippedOutro && outro != null && outro.end > outro.start && curSec >= outro.start && curSec < (outro.end - 0.5)) {
                    hasSkippedOutro = true
                    if (currentEpisode < totalEpisodes) {
                        saveProgress(100); autoSkipNotice = "⏩ Melompat ke Episode Berikutnya..."; currentEpisode += 1
                    } else {
                        exoPlayer.seekTo(((outro.end + 0.5) * 1000).toLong()); autoSkipNotice = "⏩ Lewati Penutup"
                    }
                }
                if (++tick % 10 == 0 && isPlaying) saveProgress()
            } catch (_: Exception) { break }
            delay(400)
        }
    }

    LaunchedEffect(autoSkipNotice) { if (autoSkipNotice != null) { delay(3000); autoSkipNotice = null } }
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

        val curSec = currentPosition / 1000.0
        val intro = resolvedStream?.intro
        val outro = resolvedStream?.outro
        val hasOp = intro != null && intro.end > intro.start
        val hasEd = outro != null && outro.end > outro.start
        val showIntroBtn = if (hasOp && intro != null) curSec >= intro.start && curSec < intro.end else curSec in 4.0..95.0 && duration > 120_000L
        val showOutroBtn = if (hasEd && outro != null) curSec >= outro.start && curSec < outro.end else duration > 180_000L && curSec >= ((duration / 1000.0) - 85.0)

        val onSkipIntro = {
            val target = if (hasOp && intro != null) ((intro.end + 0.5) * 1000).toLong() else (currentPosition + 85_000L).coerceAtMost(duration)
            exoPlayer.seekTo(target); hasSkippedIntro = true; ping()
        }
        val onSkipOutro = {
            hasSkippedOutro = true
            if (currentEpisode < totalEpisodes) { saveProgress(100); currentEpisode += 1 }
            else { exoPlayer.seekTo(if (hasEd && outro != null) ((outro.end + 0.5) * 1000).toLong() else duration) }
            ping()
        }
        PlayerSkipOverlay(
            autoSkipNotice = autoSkipNotice, showSkipIntroBtn = showIntroBtn, showSkipOutroBtn = showOutroBtn,
            introLabel = if (hasOp) "Lewati Pembuka >>" else "Lewati Pembuka (+85s) >>",
            outroLabel = if (currentEpisode < totalEpisodes) "Episode Berikutnya >>" else "Lewati Penutup >>",
            areControlsVisible = areControlsVisible, onSkipIntroClick = onSkipIntro, onSkipOutroClick = onSkipOutro
        )

        PlayerLockOverlay(isScreenLocked, showUnlockButton, onUnlockClick = { isScreenLocked = false; showUnlockButton = false; ping() }, modifier = Modifier.align(Alignment.Center))

        if (!isScreenLocked) {
            PlayerControlsOverlay(
                isVisible = areControlsVisible, title = anime.title, episode = currentEpisode,
                isAutoSkipEnabled = isAutoSkipEnabled, onToggleAutoSkip = { isAutoSkipEnabled = !isAutoSkipEnabled; ping() },
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
