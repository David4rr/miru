package id.tatap.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import id.tatap.app.data.AnimeCard
import id.tatap.app.data.ApiClient
import id.tatap.app.data.ResolvedStream
import id.tatap.app.data.WatchHistoryItem
import id.tatap.app.ui.components.OrangeButton
import id.tatap.app.ui.theme.CrunchyOrange
import id.tatap.app.ui.theme.DarkBackground
import id.tatap.app.ui.theme.DarkSurface
import id.tatap.app.ui.theme.TextMuted
import id.tatap.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    anime: AnimeCard,
    episode: Int,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity
    val window = activity?.window

    var resolvedStream by remember { mutableStateOf<ResolvedStream?>(null) }
    var isLoadingStream by remember { mutableStateOf(true) }
    var streamError by remember { mutableStateOf<String?>(null) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }

    var areControlsVisible by remember { mutableStateOf(true) }
    var isLandscape by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var selectedSubtitleIndex by remember { mutableStateOf(0) }

    // Immersive sticky fullscreen mode: hide system bars and keep screen on
    DisposableEffect(Unit) {
        window?.let { win ->
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insetsController = WindowCompat.getInsetsController(win, win.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            window?.let { win ->
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Build ExoPlayer instance with fast low-latency playback buffering
    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2500,
                /* maxBufferMs = */ 30000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1000
            )
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(45000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
            }
    }

    // Save watch progress to local daemon helper
    fun saveProgressNow() {
        if (duration > 0) {
            val progressPercent = ((currentPosition * 100) / duration).toInt()
            scope.launch {
                ApiClient.saveProgress(
                    slug = anime.slug,
                    episode = episode,
                    progress = progressPercent,
                    duration = (duration / 1000).toInt()
                )
            }
        }
    }

    // Back handling with state cleanup
    BackHandler {
        saveProgressNow()
        exoPlayer.stop()
        exoPlayer.release()
        onBackClick()
    }

    fun loadStream() {
        scope.launch {
            isLoadingStream = true
            isBuffering = true
            streamError = null
            ApiClient.resolveStream(anime.slug, episode, "sub", anime.idMal)
                .onSuccess { res ->
                    resolvedStream = res
                    isLoadingStream = false

                    // Build HLS MediaItem
                    val mediaItemBuilder = MediaItem.Builder()
                        .setUri(res.proxiedMasterUrl)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)

                    // Only attach the default / active subtitle track initially to prevent socket starvation
                    val defaultSub = res.subtitles.firstOrNull { it.default } ?: res.subtitles.firstOrNull()
                    if (defaultSub != null && defaultSub.url.isNotBlank()) {
                        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(defaultSub.url))
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(defaultSub.lang)
                            .setLabel(defaultSub.label)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                        mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
                    }

                    exoPlayer.setMediaItem(mediaItemBuilder.build())
                    exoPlayer.prepare()
                    exoPlayer.play()

                    // Add to watch history
                    ApiClient.addHistory(
                        WatchHistoryItem(
                            slug = anime.slug,
                            title = anime.title,
                            episode = episode,
                            poster = anime.poster,
                            type = anime.status ?: "ANIME",
                            progress = 0
                        )
                    )
                }
                .onFailure {
                    isLoadingStream = false
                    isBuffering = false
                    streamError = it.localizedMessage ?: "Gagal memuat video stream"
                }
        }
    }

    // Resolve stream on initial launch or episode change
    LaunchedEffect(anime.slug, episode) {
        loadStream()
    }

    // Periodic time & state listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                isLoadingStream = false
                val httpCause = generateSequence(error.cause) { it.cause }
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                    .firstOrNull()

                val detail = if (httpCause != null) {
                    "HTTP ${httpCause.responseCode} (${httpCause.dataSpec.uri})"
                } else {
                    error.localizedMessage ?: "Format video tidak didukung"
                }
                streamError = "Gagal memutar video (${error.errorCodeName}): $detail"
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            saveProgressNow()
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Periodic progress poller (every 500ms)
    LaunchedEffect(exoPlayer) {
        var tick = 0
        while (true) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            duration = exoPlayer.duration.coerceAtLeast(0L)
            tick++
            if (tick % 20 == 0) {
                saveProgressNow()
            }
            delay(500)
        }
    }

    // Auto-hide controls after 4 seconds of inactivity and re-hide system bars
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying) {
            delay(4000)
            areControlsVisible = false
        }
        if (!areControlsVisible) {
            window?.let { win ->
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. AndroidView wrapping ExoPlayer Surface
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
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            areControlsVisible = !areControlsVisible
                        },
                        onDoubleTap = { offset ->
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 2) {
                                val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                exoPlayer.seekTo(target)
                            } else {
                                val target = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(target)
                            }
                        }
                    )
                }
        )

        // 2. Buffering / Connecting Indicator (Only while not in error)
        if (streamError == null && (isLoadingStream || isBuffering)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = CrunchyOrange,
                        modifier = Modifier.size(54.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isLoadingStream) "Menghubungkan stream..." else "Memuat video...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. Error Overlay with Retry Button
        if (streamError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = CrunchyOrange,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Gagal Memutar Video",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = streamError ?: "Tidak dapat menghubungi server lokal",
                        fontSize = 13.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OrangeButton(
                        text = "Coba Lagi",
                        icon = Icons.Default.Refresh,
                        onClick = { loadStream() }
                    )
                }
            }
        }

        // 4. AniSkip Floating Pill Button (Lewati Intro / Outro)
        val curSec = currentPosition / 1000.0
        val intro = resolvedStream?.intro
        val outro = resolvedStream?.outro

        val showSkipIntro = intro != null && intro.end > intro.start && curSec >= intro.start && curSec < intro.end
        val showSkipOutro = outro != null && outro.end > outro.start && curSec >= outro.start && curSec < outro.end

        if (showSkipIntro || showSkipOutro) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 24.dp, bottom = 80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CrunchyOrange)
                    .clickable {
                        if (showSkipIntro && intro != null) {
                            exoPlayer.seekTo(((intro.end + 0.5) * 1000).toLong())
                        } else if (showSkipOutro && outro != null) {
                            exoPlayer.seekTo(((outro.end + 0.5) * 1000).toLong())
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (showSkipIntro) "Lewati Intro >>" else "Lewati Outro >>",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 5. Custom Crunchyroll Controls Overlay (HUD)
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                // Top Header Controls with statusBarsPadding
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        saveProgressNow()
                        exoPlayer.stop()
                        exoPlayer.release()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = anime.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "Episode $episode",
                            color = CrunchyOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Subtitles selector button
                    IconButton(onClick = { showSubtitleDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "Subtitle",
                            tint = Color.White
                        )
                    }

                    // Speed selector button
                    IconButton(onClick = { showSpeedDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Kecepatan",
                            tint = Color.White
                        )
                    }

                    // Fullscreen / Landscape Toggle button
                    IconButton(onClick = {
                        isLandscape = !isLandscape
                        activity?.requestedOrientation = if (isLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }) {
                        Icon(
                            imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isLandscape) "Keluar Layar Penuh" else "Layar Penuh",
                            tint = Color.White
                        )
                    }
                }

                // Center Play/Pause & 10s Seek Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                            exoPlayer.seekTo(target)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Mundur 10 Detik",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(CrunchyOrange)
                            .clickable {
                                if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Jeda" else "Putar",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                            exoPlayer.seekTo(target)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Maju 10 Detik",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Bottom Seekbar & Timing Row with navigationBarsPadding
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { factor ->
                                val targetMs = (factor * duration).toLong()
                                exoPlayer.seekTo(targetMs)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = CrunchyOrange,
                                activeTrackColor = CrunchyOrange,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatTime(duration),
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Subtitle Selector Dialog
        if (showSubtitleDialog) {
            val subs = resolvedStream?.subtitles ?: emptyList()
            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                containerColor = DarkSurface,
                title = { Text("Pilih Subtitle", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        subs.forEachIndexed { idx, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSubtitleIndex = idx
                                        showSubtitleDialog = false
                                        val mediaItemBuilder = MediaItem.Builder()
                                            .setUri(resolvedStream?.proxiedMasterUrl ?: "")
                                            .setMimeType(MimeTypes.APPLICATION_M3U8)

                                        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
                                            .setMimeType(MimeTypes.TEXT_VTT)
                                            .setLanguage(track.lang)
                                            .setLabel(track.label)
                                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                            .build()
                                        mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
                                        val pos = exoPlayer.currentPosition
                                        exoPlayer.setMediaItem(mediaItemBuilder.build())
                                        exoPlayer.seekTo(pos)
                                        exoPlayer.prepare()
                                        exoPlayer.play()
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = track.label,
                                    color = if (selectedSubtitleIndex == idx) CrunchyOrange else TextPrimary,
                                    fontSize = 14.sp
                                )
                                if (selectedSubtitleIndex == idx) {
                                    Text("Aktif", color = CrunchyOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDialog = false }) {
                        Text("Tutup", color = CrunchyOrange)
                    }
                }
            )
        }

        // Speed Selector Dialog
        if (showSpeedDialog) {
            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                containerColor = DarkSurface,
                title = { Text("Kecepatan Putar", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        speeds.forEach { spd ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSpeed = spd
                                        exoPlayer.playbackParameters = PlaybackParameters(spd)
                                        showSpeedDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${spd}x",
                                    color = if (selectedSpeed == spd) CrunchyOrange else TextPrimary,
                                    fontSize = 14.sp
                                )
                                if (selectedSpeed == spd) {
                                    Text("Dipilih", color = CrunchyOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) {
                        Text("Tutup", color = CrunchyOrange)
                    }
                }
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
