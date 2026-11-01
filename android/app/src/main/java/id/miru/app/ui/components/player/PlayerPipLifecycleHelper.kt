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

    DisposableEffect(Unit) {
        PlayerPipHelper.isPlayerActive = true
        PlayerPipHelper.onPlay = { exoPlayer.play() }
        PlayerPipHelper.onPause = { exoPlayer.pause() }
        PlayerPipHelper.onRewind = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) }
        PlayerPipHelper.onForward = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(duration)) }
        onDispose {
            PlayerPipHelper.isPlayerActive = false
            PlayerPipHelper.onPlay = null
            PlayerPipHelper.onPause = null
            PlayerPipHelper.onRewind = null
            PlayerPipHelper.onForward = null
        }
    }

    LaunchedEffect(isPlaying) {
        PlayerPipHelper.isPlaying = isPlaying
        (context as? Activity)?.let {
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
