package id.miru.app.ui.components.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun PlayerGestureOverlay(
    scale: Float = 1.0f,
    onScaleChanged: (Float) -> Unit = {},
    isScreenLocked: Boolean = false,
    onToggleControls: () -> Unit = {},
    onToggleUnlockButton: () -> Unit = {},
    onDoubleTapSeek: (Boolean) -> Unit = {},
    onDoubleTapResetZoom: () -> Unit = {},
    showVolumeHud: Boolean = false,
    volumePercent: Int = 50,
    showBrightnessHud: Boolean = false,
    brightnessPercent: Int = 50,
    seekRippleSide: String? = null,
    toastMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val currentScale by rememberUpdatedState(scale)
    val currentIsLocked by rememberUpdatedState(isScreenLocked)
    val currentOnToggleControls by rememberUpdatedState(onToggleControls)
    val currentOnToggleUnlockButton by rememberUpdatedState(onToggleUnlockButton)
    val currentOnDoubleTapSeek by rememberUpdatedState(onDoubleTapSeek)
    val currentOnDoubleTapResetZoom by rememberUpdatedState(onDoubleTapResetZoom)

    val transformState = rememberTransformableState { zoomChange, _, _ ->
        if (!currentIsLocked) {
            onScaleChanged(PlayerAspectRatioController.clampScale(currentScale * zoomChange))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .transformable(state = transformState, enabled = !isScreenLocked)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (currentIsLocked) {
                            currentOnToggleUnlockButton()
                        } else {
                            currentOnToggleControls()
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!currentIsLocked) {
                            if (currentScale > 1.05f) {
                                currentOnDoubleTapResetZoom()
                            } else {
                                val isLeft = offset.x < size.width / 2
                                currentOnDoubleTapSeek(isLeft)
                            }
                        }
                    }
                )
            }
    ) {
        PlayerVolumeHud(
            show = showVolumeHud,
            percent = volumePercent,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp)
        )
        PlayerBrightnessHud(
            show = showBrightnessHud,
            percent = brightnessPercent,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp)
        )
        PlayerPinchZoomHud(
            scale = scale,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
        )
        PlayerAspectRatioToastHud(
            message = toastMessage,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
        )
        PlayerSeekRippleHud(
            side = seekRippleSide,
            modifier = Modifier
                .align(if (seekRippleSide == "left") Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 60.dp)
        )
    }
}
