package id.miru.app.ui.components.player

import android.graphics.Matrix
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
object VideoEdgeCropHelper {
    const val BASE_SAFE_SCALE = 1.010f
    const val MAX_SAFE_SCALE = 1.038f
    const val SUBPIXEL_SAFETY_MARGIN = 0.008f
    const val MACROBLOCK_ALIGNMENT = 16

    fun calculateScalesFromDimensions(
        width: Int,
        height: Int,
        unappliedRotationDegrees: Int = 0
    ): Pair<Float, Float> {
        if (width <= 0 || height <= 0) {
            return Pair(1.012f, 1.012f)
        }

        val isRotated = unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270
        val rawW = if (isRotated) height else width
        val rawH = if (isRotated) width else height

        val remW = rawW % MACROBLOCK_ALIGNMENT
        val padW = if (remW != 0) MACROBLOCK_ALIGNMENT - remW else 0
        val padWFraction = padW.toFloat() / rawW.toFloat()

        val remH = rawH % MACROBLOCK_ALIGNMENT
        val padH = if (remH != 0) MACROBLOCK_ALIGNMENT - remH else 0
        val padHFraction = padH.toFloat() / rawH.toFloat()

        val scaleX = (1.0f + (2f * padWFraction) + SUBPIXEL_SAFETY_MARGIN).coerceIn(BASE_SAFE_SCALE, MAX_SAFE_SCALE)
        val scaleY = (1.0f + (2f * padHFraction) + SUBPIXEL_SAFETY_MARGIN).coerceIn(BASE_SAFE_SCALE, MAX_SAFE_SCALE)

        return Pair(scaleX, scaleY)
    }

    fun calculateCropScales(videoSize: VideoSize?): Pair<Float, Float> {
        if (videoSize == null) return Pair(1.012f, 1.012f)
        return calculateScalesFromDimensions(
            width = videoSize.width,
            height = videoSize.height,
            unappliedRotationDegrees = videoSize.unappliedRotationDegrees
        )
    }

    fun applyCropTransform(textureView: TextureView, videoSize: VideoSize?) {
        val w = textureView.width
        val h = textureView.height
        if (w <= 0 || h <= 0) return

        val (scaleX, scaleY) = calculateCropScales(videoSize)
        val matrix = Matrix()
        val rotation = videoSize?.unappliedRotationDegrees ?: 0

        if (rotation != 0) {
            matrix.postRotate(rotation.toFloat(), w / 2f, h / 2f)
            if (rotation == 90 || rotation == 270) {
                val viewAspect = h.toFloat() / w.toFloat()
                matrix.postScale(1f / viewAspect, viewAspect, w / 2f, h / 2f)
            }
        }

        matrix.postScale(scaleX, scaleY, w / 2f, h / 2f)
        textureView.setTransform(matrix)
    }
}
