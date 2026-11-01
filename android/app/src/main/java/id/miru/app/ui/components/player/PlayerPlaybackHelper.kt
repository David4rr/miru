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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import id.miru.app.data.ApiClient
import id.miru.app.data.ResolvedStream
import id.miru.app.data.SubtitleTrack

fun selectVideoTrackQuality(exoPlayer: ExoPlayer, width: Int, height: Int) {
    val builder = exoPlayer.trackSelectionParameters.buildUpon()
    if (width > 0 && height > 0) {
        builder.setMaxVideoSize(width, height)
    } else {
        builder.clearVideoSizeConstraints()
    }
    exoPlayer.trackSelectionParameters = builder.build()
}

suspend fun prepareMediaStream(
    slug: String, episode: Int, malId: Long?, title: String? = null,
    exoPlayer: ExoPlayer, resumePositionMs: Long? = null,
    mode: String = "sub", provider: String? = null, server: String? = null,
    onSuccess: (ResolvedStream) -> Unit, onFailure: (String) -> Unit
) {
    ApiClient.resolveStream(slug, episode, mode, malId, title, provider, server)
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
            if (resumePositionMs != null && resumePositionMs > 5_000L) {
                exoPlayer.seekTo(resumePositionMs)
            }
            exoPlayer.play()
        }
        .onFailure { err ->
            onFailure(err.localizedMessage ?: "Gagal memuat video stream")
        }
}

fun switchSubtitleTrack(
    exoPlayer: ExoPlayer,
    resolvedStream: ResolvedStream,
    selectedSub: SubtitleTrack?
) {
    val currentPosition = exoPlayer.currentPosition
    val isPlaying = exoPlayer.isPlaying
    val mediaItemBuilder = MediaItem.Builder()
        .setUri(resolvedStream.proxiedMasterUrl)
        .setMimeType(MimeTypes.APPLICATION_M3U8)

    if (selectedSub != null && selectedSub.url.isNotBlank()) {
        val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(selectedSub.url))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(selectedSub.lang)
            .setLabel(selectedSub.label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        mediaItemBuilder.setSubtitleConfigurations(listOf(subConfig))
    }

    exoPlayer.setMediaItem(mediaItemBuilder.build(), currentPosition)
    if (isPlaying) {
        exoPlayer.play()
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 40_000, 1_000, 2_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(10_000, true)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply { playWhenReady = true }
    }

    DisposableEffect(lifecycleOwner) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        val playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { onIsPlayingChanged(playing) }
            override fun onPlaybackStateChanged(state: Int) {
                onBufferingChanged(state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) onDurationChanged(exoPlayer.duration.coerceAtLeast(0L))
                if (state == Player.STATE_ENDED) onPlaybackEnded()
            }
        }
        exoPlayer.addListener(playerListener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                    onDisposePlayer()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            onDisposePlayer()
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    return exoPlayer
}
