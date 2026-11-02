package id.miru.app.ui.components.player

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun SyncPlayerPipLifecycle(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    duration: Long
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(exoPlayer) {
        PlayerPipHelper.isPlayerActive = true
        PlayerPipHelper.isPlaying = isPlaying
        PlayerPipHelper.onPlay = {
            exoPlayer.play()
            PlayerPipHelper.isPlaying = true
            activity?.let {
                PlayerPipHelper.updatePictureInPictureActions(it, true, PlayerPipHelper.videoWidth, PlayerPipHelper.videoHeight)
            }
        }
        PlayerPipHelper.onPause = {
            exoPlayer.pause()
            PlayerPipHelper.isPlaying = false
            activity?.let {
                PlayerPipHelper.updatePictureInPictureActions(it, false, PlayerPipHelper.videoWidth, PlayerPipHelper.videoHeight)
            }
        }
        PlayerPipHelper.onRewind = {
            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
            exoPlayer.seekTo(target)
        }
        PlayerPipHelper.onForward = {
            val totalDur = exoPlayer.duration.coerceAtLeast(0L).let { if (it > 0L) it else duration.coerceAtLeast(0L) }
            val maxDur = if (totalDur > 0L) totalDur else Long.MAX_VALUE
            val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
            exoPlayer.seekTo(target)
        }
        onDispose {
            PlayerPipHelper.isPlayerActive = false
            PlayerPipHelper.isEnteringPip = false
            PlayerPipHelper.onPlay = null
            PlayerPipHelper.onPause = null
            PlayerPipHelper.onRewind = null
            PlayerPipHelper.onForward = null
            activity?.let { PlayerPipHelper.resetPictureInPictureParams(it) }
        }
    }

    LaunchedEffect(isPlaying, PlayerPipHelper.videoWidth, PlayerPipHelper.videoHeight) {
        PlayerPipHelper.isPlaying = isPlaying
        activity?.let {
            PlayerPipHelper.updatePictureInPictureActions(it, isPlaying, PlayerPipHelper.videoWidth, PlayerPipHelper.videoHeight)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    PlayerPipHelper.videoWidth = videoSize.width
                    PlayerPipHelper.videoHeight = videoSize.height
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
}
