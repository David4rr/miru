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
    val activity = context as? Activity
    val window = activity?.window
    val scope = rememberCoroutineScope()
    var currentEpisode by remember { mutableIntStateOf(episode) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isLoadingStream by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showUnlockButton by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isAutoSkipEnabled by remember { mutableStateOf(true) }
    var autoSkipNotice by remember { mutableStateOf<String?>(null) }
    var showEpisodeSheet by remember { mutableStateOf(false) }
    var resolvedStream by remember { mutableStateOf<ResolvedStream?>(null) }
    var hasSkippedIntro by remember(currentEpisode) { mutableStateOf(false) }
    var hasSkippedOutro by remember(currentEpisode) { mutableStateOf(false) }
    var seekRippleSide by remember { mutableStateOf<String?>(null) }
    val totalEpisodes = if (anime.isNotYetReleased) 0 else (anime.episodes ?: 12)

    fun saveProgress(explicitProgress: Int? = null) {
        saveWatchProgressAsync(anime.slug, currentEpisode, currentPosition, duration, explicitProgress)
    }

    val exoPlayer = rememberExoPlayerWithLifecycle(
        context = context, activity = activity, window = window,
        onIsPlayingChanged = { isPlaying = it },
        onBufferingChanged = { isBuffering = it },
        onDurationChanged = { duration = it },
        onPlaybackEnded = {
            saveProgress(100)
            if (currentEpisode < totalEpisodes) currentEpisode += 1
        },
        onDisposePlayer = { saveProgress() }
    )

    LaunchedEffect(currentEpisode) {
        isLoadingStream = true
        isBuffering = true
        prepareMediaStream(
            slug = anime.slug, episode = currentEpisode, malId = anime.idMal, exoPlayer = exoPlayer,
            onSuccess = { res -> resolvedStream = res; isLoadingStream = false; saveProgress(0) },
            onFailure = { isLoadingStream = false; isBuffering = false }
        )
    }

    LaunchedEffect(exoPlayer, isAutoSkipEnabled, resolvedStream, currentEpisode) {
        var tick = 0
        while (true) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (duration <= 0 && exoPlayer.duration > 0) duration = exoPlayer.duration

            if (isAutoSkipEnabled && isPlaying) {
                val curSec = currentPosition / 1000.0
                val intro = resolvedStream?.intro
                val outro = resolvedStream?.outro

                if (!hasSkippedIntro && intro != null && intro.end > intro.start && curSec >= intro.start && curSec < (intro.end - 1.0)) {
                    exoPlayer.seekTo(((intro.end + 0.5) * 1000).toLong())
                    hasSkippedIntro = true
                    autoSkipNotice = "⏩ Lewati Intro Otomatis (+${(intro.end - intro.start).toInt()}d)"
                }

                if (!hasSkippedOutro && outro != null && outro.end > outro.start && curSec >= outro.start && curSec < (outro.end - 2.0)) {
                    hasSkippedOutro = true
                    saveProgress(100)
                    if (currentEpisode < totalEpisodes) {
                        autoSkipNotice = "⏩ Lewati Penutup (Episode Berikutnya)"
                        currentEpisode += 1
                    } else {
                        exoPlayer.seekTo(((outro.end + 0.5) * 1000).toLong())
                        autoSkipNotice = "⏩ Lewati Penutup"
                    }
                }
            }

            tick++
            if (tick % 10 == 0 && isPlaying) saveProgress()
            delay(400)
        }
    }

    LaunchedEffect(autoSkipNotice) {
        if (autoSkipNotice != null) { delay(3000); autoSkipNotice = null }
    }

    BackHandler {
        saveProgress()
        exoPlayer.stop()
        onBackClick()
    }

    Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        PlayerVideoSurface(
            exoPlayer = exoPlayer, isScreenLocked = isScreenLocked,
            onToggleControls = { areControlsVisible = !areControlsVisible },
            onToggleUnlockButton = { showUnlockButton = !showUnlockButton },
            onDoubleTapSeek = { isLeft ->
                val delta = if (isLeft) -10_000 else 10_000
                exoPlayer.seekTo((exoPlayer.currentPosition + delta).coerceIn(0, duration))
                seekRippleSide = if (isLeft) "left" else "right"
                scope.launch { delay(600); seekRippleSide = null }
            }
        )

        PlayerGestureOverlay(false, 50, false, 50, seekRippleSide)

        val curSec = currentPosition / 1000.0
        val intro = resolvedStream?.intro
        val outro = resolvedStream?.outro
        val showSkipIntro = intro != null && intro.end > intro.start && curSec >= intro.start && curSec < intro.end
        val showSkipOutro = outro != null && outro.end > outro.start && curSec >= outro.start && curSec < outro.end

        PlayerSkipOverlay(
            autoSkipNotice = autoSkipNotice, showSkipIntroBtn = showSkipIntro, showSkipOutroBtn = showSkipOutro,
            onSkipClick = {
                if (showSkipIntro && intro != null) exoPlayer.seekTo(((intro.end + 0.5) * 1000).toLong())
                else if (showSkipOutro && outro != null) {
                    saveProgress(100)
                    if (currentEpisode < totalEpisodes) currentEpisode += 1 else exoPlayer.seekTo(((outro.end + 0.5) * 1000).toLong())
                }
            }
        )

        PlayerLockOverlay(isScreenLocked, showUnlockButton, onUnlockClick = { isScreenLocked = false; showUnlockButton = false; areControlsVisible = true }, modifier = Modifier.align(Alignment.Center))

        if (!isScreenLocked) {
            PlayerControlsOverlay(
                isVisible = areControlsVisible, title = anime.title, episode = currentEpisode,
                isAutoSkipEnabled = isAutoSkipEnabled, onToggleAutoSkip = { isAutoSkipEnabled = !isAutoSkipEnabled },
                onToggleLock = { isScreenLocked = true; areControlsVisible = false; showUnlockButton = true },
                onBackClick = { saveProgress(); exoPlayer.stop(); onBackClick() },
                isPlaying = isPlaying, isBuffering = isBuffering, isLoadingStream = isLoadingStream,
                hasNextEpisode = currentEpisode < totalEpisodes, onPlayPauseClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                onRewind10Click = { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0)) },
                onForward10Click = { exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(duration)) },
                onNextEpisodeClick = { if (currentEpisode < totalEpisodes) currentEpisode += 1 },
                currentPosition = currentPosition, duration = duration, playbackSpeed = playbackSpeed,
                onSeek = { target -> exoPlayer.seekTo(target) },
                onCycleSpeed = {
                    val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
                    playbackSpeed = speeds[(speeds.indexOf(playbackSpeed) + 1) % speeds.size]
                    exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                },
                onOpenEpisodeSheet = { showEpisodeSheet = true }
            )
        }

        if (showEpisodeSheet) {
            PlayerEpisodeSheet(totalEpisodes = totalEpisodes, currentEpisode = currentEpisode, onSelectEpisode = { ep -> currentEpisode = ep }, onDismissRequest = { showEpisodeSheet = false })
        }
    }
}
