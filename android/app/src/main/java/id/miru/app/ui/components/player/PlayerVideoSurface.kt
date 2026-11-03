package id.miru.app.ui.components.player

import android.graphics.Matrix
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
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
                            applySafeTextureCrop(textureView)
                        }
                        applySafeTextureCrop(textureView)
                    }
                }
            },
            update = { playerView ->
                exoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                playerView.player = exoPlayer
                playerView.resizeMode = aspectRatioMode.resizeMode
                playerView.subtitleView?.visibility = View.GONE
                (playerView.videoSurfaceView as? TextureView)?.let { textureView ->
                    applySafeTextureCrop(textureView)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )

        PlayerSubtitleOverlay(
            exoPlayer = exoPlayer,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun applySafeTextureCrop(textureView: TextureView) {
    val w = textureView.width
    val h = textureView.height
    if (w > 0 && h > 0) {
        val matrix = Matrix()
        matrix.setScale(1.002f, 1.004f, w / 2f, h / 2f)
        textureView.setTransform(matrix)
    }
}
