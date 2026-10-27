package id.miru.app.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun PlayerControlsOverlay(
    isVisible: Boolean,
    title: String,
    episode: Int,
    isAutoSkipEnabled: Boolean,
    onToggleAutoSkip: () -> Unit,
    onToggleLock: () -> Unit,
    onBackClick: () -> Unit,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isLoadingStream: Boolean,
    hasNextEpisode: Boolean,
    onPlayPauseClick: () -> Unit,
    onRewind10Click: () -> Unit,
    onForward10Click: () -> Unit,
    onNextEpisodeClick: () -> Unit,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    onSeek: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onOpenEpisodeSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            PlayerTopControls(
                title = title,
                episode = episode,
                isAutoSkipEnabled = isAutoSkipEnabled,
                onToggleAutoSkip = onToggleAutoSkip,
                isScreenLocked = false,
                onToggleLock = onToggleLock,
                onBackClick = onBackClick,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            PlayerCenterControls(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                isLoadingStream = isLoadingStream,
                hasNextEpisode = hasNextEpisode,
                onPlayPauseClick = onPlayPauseClick,
                onRewind10Click = onRewind10Click,
                onForward10Click = onForward10Click,
                onNextEpisodeClick = onNextEpisodeClick,
                modifier = Modifier.align(Alignment.Center)
            )

            PlayerBottomControls(
                currentPosition = currentPosition,
                duration = duration,
                playbackSpeed = playbackSpeed,
                onSeek = onSeek,
                onCycleSpeed = onCycleSpeed,
                onOpenEpisodeSheet = onOpenEpisodeSheet,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
