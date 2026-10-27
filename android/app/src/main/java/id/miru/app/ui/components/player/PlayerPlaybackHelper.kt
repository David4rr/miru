package id.miru.app.ui.components.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.Window
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import id.miru.app.data.ApiClient
import id.miru.app.data.ResolvedStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun saveWatchProgressAsync(slug: String, episode: Int, currentPosition: Long, duration: Long, explicitProgress: Int? = null) {
    val dur = duration.coerceAtLeast(1L)
    val curPos = currentPosition.coerceAtLeast(0L)
    val progressPercent = explicitProgress ?: ((curPos * 100) / dur).toInt().coerceIn(0, 100)
    CoroutineScope(Dispatchers.IO).launch {
        withContext(NonCancellable) {
            ApiClient.saveProgress(slug = slug, episode = episode, progress = progressPercent, duration = (dur / 1000).toInt())
        }
    }
}

suspend fun prepareMediaStream(
    slug: String,
    episode: Int,
    malId: Long?,
    exoPlayer: ExoPlayer,
    onSuccess: (ResolvedStream) -> Unit,
    onFailure: (String) -> Unit
) {
    ApiClient.resolveStream(slug, episode, "sub", malId)
        .onSuccess { res ->
            onSuccess(res)
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(res.proxiedMasterUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)

            val defaultSub = res.subtitles.firstOrNull { it.default } ?: res.subtitles.firstOrNull()
            if (defaultSub != null && defaultSub.url.isNotBlank()) {
                val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(defaultSub.url))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(defaultSub.lang)
                    .setLabel(defaultSub.label)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                mediaItemBuilder.setSubtitleConfigurations(listOf(subConfig))
            }
            exoPlayer.setMediaItem(mediaItemBuilder.build())
            exoPlayer.prepare()
            exoPlayer.play()
        }
        .onFailure { err ->
            onFailure(err.localizedMessage ?: "Gagal memuat video stream")
        }
}

@OptIn(UnstableApi::class)
@Composable
fun rememberExoPlayerWithLifecycle(
    context: Context,
    activity: Activity?,
    window: Window?,
    onIsPlayingChanged: (Boolean) -> Unit,
    onBufferingChanged: (Boolean) -> Unit,
    onDurationChanged: (Long) -> Unit,
    onPlaybackEnded: () -> Unit,
    onDisposePlayer: () -> Unit
): ExoPlayer {
    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_500, 3_000)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply { playWhenReady = true }
    }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { onIsPlayingChanged(playing) }
            override fun onPlaybackStateChanged(state: Int) {
                onBufferingChanged(state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) onDurationChanged(exoPlayer.duration.coerceAtLeast(0L))
                if (state == Player.STATE_ENDED) onPlaybackEnded()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            onDisposePlayer()
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    return exoPlayer
}
