package id.miru.app.ui.components.player

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerGestureOverlay(
    scale: Float = 1.0f,
    onScaleChanged: (Float) -> Unit = {},
    isScreenLocked: Boolean = false,
    onToggleControls: () -> Unit = {},
    onToggleUnlockButton: () -> Unit = {},
    onDoubleTapSeek: (Boolean) -> Unit = {},
    onDoubleTapResetZoom: () -> Unit = {},
    showVolumeHud: Boolean? = null,
    volumePercent: Int? = null,
    showBrightnessHud: Boolean? = null,
    brightnessPercent: Int? = null,
    seekRippleSide: String? = null,
    toastMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val audioBrightnessController = rememberPlayerAudioBrightnessController()
    val scope = rememberCoroutineScope()

    var internalSeekSide by remember { mutableStateOf<String?>(null) }
    var internalSeekSeconds by remember { mutableIntStateOf(10) }
    var activeSeekSide by remember { mutableStateOf<String?>(null) }
    var accumulatedSeconds by remember { mutableIntStateOf(0) }
    var pendingSingleTapJob by remember { mutableStateOf<Job?>(null) }
    var seekTimeoutJob by remember { mutableStateOf<Job?>(null) }

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
                detectPlayerGestures(
                    onTap = { position ->
                        if (currentIsLocked) {
                            currentOnToggleUnlockButton()
                            return@detectPlayerGestures
                        }
                        if (seekTimeoutJob?.isActive == true && activeSeekSide != null) {
                            val isLeft = position.x < size.width / 2f
                            val side = if (isLeft) "left" else "right"
                            accumulatedSeconds = if (side == activeSeekSide) accumulatedSeconds + 10 else 10
                            activeSeekSide = side
                            internalSeekSide = side
                            internalSeekSeconds = accumulatedSeconds
                            currentOnDoubleTapSeek(isLeft)

                            seekTimeoutJob?.cancel()
                            seekTimeoutJob = scope.launch {
                                delay(650)
                                internalSeekSide = null
                                activeSeekSide = null
                                accumulatedSeconds = 0
                                seekTimeoutJob = null
                            }
                            return@detectPlayerGestures
                        }
                        if (pendingSingleTapJob?.isActive == true) {
                            pendingSingleTapJob?.cancel()
                            pendingSingleTapJob = null
                            if (currentScale > 1.05f) {
                                currentOnDoubleTapResetZoom()
                                return@detectPlayerGestures
                            }
                            val isLeft = position.x < size.width / 2f
                            activeSeekSide = if (isLeft) "left" else "right"
                            accumulatedSeconds = 10
                            internalSeekSide = activeSeekSide
                            internalSeekSeconds = accumulatedSeconds
                            currentOnDoubleTapSeek(isLeft)

                            seekTimeoutJob?.cancel()
                            seekTimeoutJob = scope.launch {
                                delay(650)
                                internalSeekSide = null
                                activeSeekSide = null
                                accumulatedSeconds = 0
                                seekTimeoutJob = null
                            }
                            return@detectPlayerGestures
                        }
                        pendingSingleTapJob = scope.launch {
                            delay(300)
                            currentOnToggleControls()
                            pendingSingleTapJob = null
                        }
                    },
                    onDragBrightnessStart = { audioBrightnessController.onDragBrightnessStart() },
                    onDragBrightness = { deltaY, totalHeight -> audioBrightnessController.onDragBrightness(deltaY, totalHeight) },
                    onDragBrightnessEnd = { audioBrightnessController.onDragBrightnessEnd() },
                    onDragVolumeStart = { audioBrightnessController.onDragVolumeStart() },
                    onDragVolume = { deltaY, totalHeight -> audioBrightnessController.onDragVolume(deltaY, totalHeight) },
                    onDragVolumeEnd = { audioBrightnessController.onDragVolumeEnd() },
                    isLocked = { currentIsLocked },
                    onSlopExceeded = {
                        pendingSingleTapJob?.cancel()
                        pendingSingleTapJob = null
                        if (seekTimeoutJob?.isActive == true) {
                            seekTimeoutJob?.cancel()
                            internalSeekSide = null
                            activeSeekSide = null
                            accumulatedSeconds = 0
                            seekTimeoutJob = null
                        }
                    }
                )
            }
    ) {
        PlayerBrightnessHud(
            show = showBrightnessHud ?: audioBrightnessController.showBrightnessHud,
            percent = brightnessPercent ?: audioBrightnessController.brightnessPercent,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp)
        )
        PlayerVolumeHud(
            show = showVolumeHud ?: audioBrightnessController.showVolumeHud,
            percent = volumePercent ?: audioBrightnessController.volumePercent,
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
        val activeRippleSide = seekRippleSide ?: internalSeekSide
        PlayerSeekRippleHud(
            side = activeRippleSide,
            seconds = internalSeekSeconds,
            modifier = Modifier
                .align(if (activeRippleSide == "left") Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 60.dp)
        )
    }
}
