package id.miru.app.ui.components.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun PlayerVideoSurface(
    exoPlayer: ExoPlayer,
    isScreenLocked: Boolean,
    onToggleControls: () -> Unit,
    onToggleUnlockButton: () -> Unit,
    onDoubleTapSeek: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isScreenLocked) {
                if (isScreenLocked) {
                    detectTapGestures { onToggleUnlockButton() }
                } else {
                    detectTapGestures(
                        onTap = { onToggleControls() },
                        onDoubleTap = { offset ->
                            val isLeft = offset.x < size.width / 2
                            onDoubleTapSeek(isLeft)
                        }
                    )
                }
            }
    )
}
