package id.miru.app.ui.components.player

import id.miru.app.data.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun saveWatchProgressAsync(
    slug: String,
    episode: Int,
    currentPosition: Long,
    duration: Long,
    explicitProgress: Int? = null,
    title: String? = null,
    poster: String? = null,
    type: String? = null,
    mode: String = "sub"
) {
    val dur = duration.coerceAtLeast(1L)
    val curPos = currentPosition.coerceAtLeast(0L)
    val progressPercent = explicitProgress ?: ((curPos * 100) / dur).toInt().coerceIn(0, 100)
    CoroutineScope(Dispatchers.IO).launch {
        withContext(NonCancellable) {
            ApiClient.saveProgress(
                slug = slug,
                episode = episode,
                progress = progressPercent,
                duration = (dur / 1000).toInt(),
                title = title,
                poster = poster,
                type = type,
                mode = mode
            )
        }
    }
}

suspend fun fetchResumePositionMs(slug: String, episode: Int, mode: String = "sub"): Long? {
    val saved = ApiClient.getProgress(slug, episode, mode).getOrNull() ?: return null
    return if (saved.progress in 5..85 && saved.duration > 0) {
        (saved.duration * saved.progress / 100L) * 1000L
    } else null
}

fun cycleNextSpeed(current: Float): Float {
    val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
    return speeds[(speeds.indexOf(current) + 1) % speeds.size]
}
