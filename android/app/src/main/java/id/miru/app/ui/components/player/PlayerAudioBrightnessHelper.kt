package id.miru.app.ui.components.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Stable
class PlayerAudioBrightnessController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val activity = context as? Activity

    var showVolumeHud by mutableStateOf(false)
        private set
    var volumePercent by mutableIntStateOf(50)
        private set
    var showBrightnessHud by mutableStateOf(false)
        private set
    var brightnessPercent by mutableIntStateOf(50)
        private set

    private var volumeHideJob: Job? = null
    private var brightnessHideJob: Job? = null
    private var currentVolumeFloat = 0f
    private var lastAppliedVol = -1
    private var currentBrightness = 0.5f

    init {
        initVolume()
        initBrightness()
    }

    private fun readCurrentBrightness(): Float {
        val lpBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        return if (lpBrightness in 0.01f..1.0f) {
            lpBrightness
        } else {
            try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255.0f
            } catch (_: Exception) {
                0.5f
            }
        }.coerceIn(0.01f, 1.0f)
    }

    private fun initVolume() {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        currentVolumeFloat = cur.toFloat()
        lastAppliedVol = cur
        volumePercent = if (max > 0) ((cur.toFloat() / max) * 100).roundToInt().coerceIn(0, 100) else 0
    }

    private fun initBrightness() {
        currentBrightness = readCurrentBrightness()
        brightnessPercent = (currentBrightness * 100).roundToInt().coerceIn(1, 100)
    }

    fun onDragBrightnessStart() {
        brightnessHideJob?.cancel()
        initBrightness()
        showBrightnessHud = true
    }

    fun onDragBrightness(deltaY: Float, totalHeight: Float) {
        if (totalHeight <= 0f) return
        val change = -(deltaY / (totalHeight * 0.75f))
        currentBrightness = (currentBrightness + change).coerceIn(0.01f, 1.0f)
        brightnessPercent = (currentBrightness * 100).roundToInt().coerceIn(1, 100)
        activity?.window?.let { window ->
            val lp = window.attributes
            lp.screenBrightness = currentBrightness
            window.attributes = lp
        }
    }

    fun onDragBrightnessEnd() {
        brightnessHideJob?.cancel()
        brightnessHideJob = scope.launch {
            delay(1500)
            showBrightnessHud = false
        }
    }

    fun onDragVolumeStart() {
        volumeHideJob?.cancel()
        initVolume()
        showVolumeHud = true
    }

    fun onDragVolume(deltaY: Float, totalHeight: Float) {
        val am = audioManager ?: return
        if (totalHeight <= 0f) return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        val change = -(deltaY / (totalHeight * 0.75f)) * max
        currentVolumeFloat = (currentVolumeFloat + change).coerceIn(0f, max.toFloat())
        val newVol = currentVolumeFloat.roundToInt().coerceIn(0, max)
        if (newVol != lastAppliedVol) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
            lastAppliedVol = newVol
        }
        volumePercent = ((currentVolumeFloat / max) * 100).roundToInt().coerceIn(0, 100)
    }

    fun onDragVolumeEnd() {
        volumeHideJob?.cancel()
        volumeHideJob = scope.launch {
            delay(1500)
            showVolumeHud = false
        }
    }

    fun onCancelAll() {
        onDragBrightnessEnd()
        onDragVolumeEnd()
    }
}

@Composable
fun rememberPlayerAudioBrightnessController(): PlayerAudioBrightnessController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context) { PlayerAudioBrightnessController(context, scope) }
}
