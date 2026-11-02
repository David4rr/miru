package id.miru.app.ui.components.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlin.math.abs

enum class PlayerDragType {
    NONE, BRIGHTNESS, VOLUME, OTHER
}

suspend fun PointerInputScope.detectPlayerGestures(
    onTap: (Offset) -> Unit,
    onDragBrightnessStart: () -> Unit,
    onDragBrightness: (deltaY: Float, totalHeight: Float) -> Unit,
    onDragBrightnessEnd: () -> Unit,
    onDragVolumeStart: () -> Unit,
    onDragVolume: (deltaY: Float, totalHeight: Float) -> Unit,
    onDragVolumeEnd: () -> Unit,
    isLocked: () -> Boolean,
    onSlopExceeded: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downId = down.id
        val downPosition = down.position
        val downTime = System.currentTimeMillis()
        var lastY = downPosition.y
        var hasExceededSlop = false
        var isDrag = false
        var isMultiTouch = false
        var dragType = PlayerDragType.NONE

        while (true) {
            val event = awaitPointerEvent()

            if (event.changes.count { it.pressed } > 1) {
                isMultiTouch = true
                if (dragType == PlayerDragType.BRIGHTNESS) {
                    onDragBrightnessEnd()
                } else if (dragType == PlayerDragType.VOLUME) {
                    onDragVolumeEnd()
                }
                dragType = PlayerDragType.NONE
                break
            }

            val pointer = event.changes.firstOrNull { it.id == downId } ?: break
            if (!pointer.pressed) {
                break
            }

            val currentPos = pointer.position
            val totalOffset = currentPos - downPosition

            if (!hasExceededSlop) {
                val touchSlop = viewConfiguration.touchSlop
                if (abs(totalOffset.y) >= touchSlop || abs(totalOffset.x) >= touchSlop) {
                    hasExceededSlop = true
                    onSlopExceeded()
                    if (!isLocked() && abs(totalOffset.y) > abs(totalOffset.x)) {
                        val isLeft = downPosition.x < size.width / 2f
                        dragType = if (isLeft) PlayerDragType.BRIGHTNESS else PlayerDragType.VOLUME
                        if (dragType == PlayerDragType.BRIGHTNESS) {
                            onDragBrightnessStart()
                        } else {
                            onDragVolumeStart()
                        }
                        isDrag = true
                        lastY = currentPos.y
                    } else {
                        dragType = PlayerDragType.OTHER
                        isDrag = true
                    }
                }
            }

            if (isDrag) {
                if (dragType == PlayerDragType.BRIGHTNESS || dragType == PlayerDragType.VOLUME) {
                    val deltaY = currentPos.y - lastY
                    lastY = currentPos.y
                    if (dragType == PlayerDragType.BRIGHTNESS) {
                        onDragBrightness(deltaY, size.height.toFloat())
                    } else {
                        onDragVolume(deltaY, size.height.toFloat())
                    }
                    pointer.consume()
                }
            }
        }

        if (isDrag) {
            if (dragType == PlayerDragType.BRIGHTNESS) {
                onDragBrightnessEnd()
            } else if (dragType == PlayerDragType.VOLUME) {
                onDragVolumeEnd()
            }
        } else if (!isMultiTouch) {
            val elapsed = System.currentTimeMillis() - downTime
            if (elapsed < 500) {
                onTap(downPosition)
            }
        }
    }
}
