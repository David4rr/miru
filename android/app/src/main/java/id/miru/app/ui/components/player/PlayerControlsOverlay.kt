package id.miru.app.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun PlayerControlsOverlay(
    isVisible: Boolean,
    title: String,
    episode: Int,
    isSubtitleActive: Boolean = true,
    onOpenSubtitleSheet: () -> Unit,
    onToggleLock: () -> Unit,
    onBackClick: () -> Unit,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isLoadingStream: Boolean,
    hasPreviousEpisode: Boolean = false,
    hasNextEpisode: Boolean,
    errorMessage: String? = null,
    onRetryClick: (() -> Unit)? = null,
    onPlayPauseClick: () -> Unit,
    onPreviousEpisodeClick: () -> Unit = {},
    onRewind10Click: () -> Unit,
    onForward10Click: () -> Unit,
    onNextEpisodeClick: () -> Unit,
    duration: Long,
    playbackSpeed: Float,
    onSeek: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onOpenEpisodeSheet: () -> Unit,
    onBackgroundClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        val interactionSource = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onBackgroundClick() }
        ) {
            // Top Gradient Scrim & Controls
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                PlayerTopControls(
                    title = title,
                    episode = episode,
                    isScreenLocked = false,
                    isSubtitleActive = isSubtitleActive,
                    onOpenSubtitleSheet = onOpenSubtitleSheet,
                    onToggleLock = onToggleLock,
                    onBackClick = onBackClick
                )
            }

            // Center Playback Controls
            PlayerCenterControls(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                isLoadingStream = isLoadingStream,
                hasPreviousEpisode = hasPreviousEpisode,
                hasNextEpisode = hasNextEpisode,
                errorMessage = errorMessage,
                onRetryClick = onRetryClick,
                onPlayPauseClick = onPlayPauseClick,
                onPreviousEpisodeClick = onPreviousEpisodeClick,
                onRewind10Click = onRewind10Click,
                onForward10Click = onForward10Click,
                onNextEpisodeClick = onNextEpisodeClick,
                modifier = Modifier.align(Alignment.Center)
            )

            // Bottom Gradient Scrim & Controls
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.50f),
                                Color.Black.copy(alpha = 0.90f)
                            )
                        )
                    )
            ) {
                PlayerBottomControls(
                    currentPosition = currentPosition,
                    duration = duration,
                    playbackSpeed = playbackSpeed,
                    onSeek = onSeek,
                    onCycleSpeed = onCycleSpeed,
                    onOpenEpisodeSheet = onOpenEpisodeSheet
                )
            }
        }
    }
}
