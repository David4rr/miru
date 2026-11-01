package id.miru.app.ui.components.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout

@androidx.annotation.OptIn(UnstableApi::class)
enum class AspectRatioMode(
    val label: String,
    val toastMessage: String,
    val resizeMode: Int
) {
    FIT("Pas Layar", "Pas ke Layar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Isi Layar", "Isi Layar (Stretch)", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Perbesar", "Perbesar (Center Crop)", AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

    fun next(): AspectRatioMode {
        return when (this) {
            FIT -> FILL
            FILL -> ZOOM
            ZOOM -> FIT
        }
    }
}

object PlayerAspectRatioController {
    const val MIN_SCALE = 1.0f
    const val MAX_SCALE = 3.0f

    fun clampScale(scale: Float): Float {
        return scale.coerceIn(MIN_SCALE, MAX_SCALE)
    }

    fun isZoomed(scale: Float): Boolean {
        return scale > 1.01f
    }
}
