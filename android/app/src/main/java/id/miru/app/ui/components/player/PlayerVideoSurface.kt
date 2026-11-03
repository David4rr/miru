package id.miru.app.ui.components.player

import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import id.miru.app.R

@OptIn(UnstableApi::class)
@Composable
fun PlayerVideoSurface(
    exoPlayer: ExoPlayer,
    aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    scale: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    var videoSize by remember(exoPlayer) {
        mutableStateOf(exoPlayer.videoSize)
    }

    DisposableEffect(exoPlayer) {
        videoSize = exoPlayer.videoSize
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(newSize: VideoSize) {
                videoSize = newSize
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        AndroidView(
            factory = { ctx ->
                val playerView = (LayoutInflater.from(ctx).inflate(R.layout.miru_player_view, null) as? PlayerView)
                    ?: error("Failed to inflate PlayerView from layout miru_player_view")

                exoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING

                playerView.apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = aspectRatioMode.resizeMode
                    subtitleView?.visibility = View.GONE
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    (videoSurfaceView as? TextureView)?.let { textureView ->
                        textureView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                            VideoEdgeCropHelper.applyCropTransform(textureView, exoPlayer.videoSize)
                        }
                        VideoEdgeCropHelper.applyCropTransform(textureView, exoPlayer.videoSize)
                    }
                }
            },
            update = { playerView ->
                exoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                playerView.player = exoPlayer
                playerView.resizeMode = aspectRatioMode.resizeMode
                playerView.subtitleView?.visibility = View.GONE
                (playerView.videoSurfaceView as? TextureView)?.let { textureView ->
                    VideoEdgeCropHelper.applyCropTransform(textureView, videoSize)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset.Zero,
                        size = size,
                        style = Stroke(width = 2f)
                    )
                }
        )

        PlayerSubtitleOverlay(
            exoPlayer = exoPlayer,
            modifier = Modifier.fillMaxSize()
        )
    }
}
