package id.miru.app.ui.components.player

import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

@OptIn(UnstableApi::class)
@Composable
fun PlayerSubtitleOverlay(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    var cues by remember(exoPlayer) {
        mutableStateOf(exoPlayer.currentCues.cues)
    }

    DisposableEffect(exoPlayer) {
        cues = exoPlayer.currentCues.cues
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                cues = cueGroup.cues
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    AndroidView(
        factory = { ctx ->
            SubtitleView(ctx).apply {
                setStyle(
                    CaptionStyleCompat(
                        Color.WHITE,
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        Color.BLACK,
                        Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    )
                )
                setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 0.88f)
                setBottomPaddingFraction(0.08f)
                setApplyEmbeddedStyles(false)
                setApplyEmbeddedFontSizes(false)
                isClickable = false
                isFocusable = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setCues(cues)
            }
        },
        update = { subtitleView ->
            subtitleView.setCues(cues)
        },
        modifier = modifier
    )
}
