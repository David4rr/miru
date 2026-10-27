package id.tatap.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import kotlin.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
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
import id.tatap.app.data.WatchProgressItem
import id.tatap.app.ui.components.OrangeButton
import id.tatap.app.ui.components.ReportIssueBottomSheet
import id.tatap.app.ui.theme.CrunchyOrange
import id.tatap.app.ui.theme.DarkBackground
import id.tatap.app.ui.theme.DarkSurface
import id.tatap.app.ui.theme.DarkSurfaceVariant
import id.tatap.app.ui.theme.TextMuted
import id.tatap.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class SeekFeedback(val isForward: Boolean, val timestamp: Long)

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
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

    var currentEpisode by remember(episode) { mutableIntStateOf(episode) }
    val totalEpisodes = anime.episodesReleased ?: anime.episodes ?: 12

    var resolvedStream by remember { mutableStateOf<ResolvedStream?>(null) }
    var isLoadingStream by remember { mutableStateOf(true) }
    var streamError by remember { mutableStateOf<String?>(null) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }

    var areControlsVisible by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showEpisodeSheet by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }
    var selectedSubtitleIndex by remember { mutableStateOf(0) }

    // Double-tap seek visual feedback
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }

    // Brightness and Volume HUD
    var gestureHudType by remember { mutableStateOf<String?>(null) } // "brightness" or "volume"
    var gestureHudValue by remember { mutableFloatStateOf(0f) }
    var gestureHudTimestamp by remember { mutableLongStateOf(0L) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxAudioVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    LaunchedEffect(gestureHudTimestamp) {
        if (gestureHudType != null) {
            delay(1200)
            gestureHudType = null
        }
    }

    LaunchedEffect(seekFeedback?.timestamp) {
        if (seekFeedback != null) {
            delay(750)
            seekFeedback = null
        }
    }

    // Immersive sticky fullscreen mode
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

    // Build ExoPlayer instance with low-latency playback buffering
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

    fun saveProgressNow() {
        if (duration > 0) {
            val progressPercent = ((currentPosition * 100) / duration).toInt()
            scope.launch {
                ApiClient.saveProgress(
                    slug = anime.slug,
                    episode = currentEpisode,
                    progress = progressPercent,
                    duration = (duration / 1000).toInt()
                )
            }
        }
    }

    fun loadStream() {
        scope.launch {
            isLoadingStream = true
            isBuffering = true
            streamError = null

            ApiClient.resolveStream(anime.slug, currentEpisode, "sub", anime.idMal)
                .onSuccess { res ->
                    resolvedStream = res
                    isLoadingStream = false

                    val mediaItemBuilder = MediaItem.Builder()
                        .setUri(res.proxiedMasterUrl)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)

                    // Attach only the single default / active subtitle initially
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

                    ApiClient.addHistory(
                        WatchHistoryItem(
                            slug = anime.slug,
                            title = anime.title,
                            episode = currentEpisode,
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

    LaunchedEffect(anime.slug, currentEpisode) {
        loadStream()
    }

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

    // Periodic progress poller
    LaunchedEffect(exoPlayer) {
        var tick = 0
        while (true) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (duration <= 0 && exoPlayer.duration > 0) {
                duration = exoPlayer.duration
            }
            tick++
            if (tick % 10 == 0 && isPlaying) {
                saveProgressNow()
            }
            delay(500)
        }
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(areControlsVisible, isPlaying, isBuffering, isLoadingStream) {
        if (areControlsVisible && isPlaying && !isBuffering && !isLoadingStream) {
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

    BackHandler {
        saveProgressNow()
        exoPlayer.stop()
        exoPlayer.release()
        onBackClick()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 1. Video Surface with Tap and Drag Gestures
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
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Overlay Layer (Tap, Double Tap, Vertical Drag for Brightness & Volume)
        Box(
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
                                seekFeedback = SeekFeedback(isForward = false, timestamp = System.currentTimeMillis())
                            } else {
                                val target = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(target)
                                seekFeedback = SeekFeedback(isForward = true, timestamp = System.currentTimeMillis())
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val isLeft = change.position.x < size.width / 2
                            if (isLeft) {
                                // Screen Brightness adjustment
                                val win = window
                                if (win != null) {
                                    val currentBrightness = if (win.attributes.screenBrightness < 0) 0.5f else win.attributes.screenBrightness
                                    val delta = -dragAmount / 450f
                                    val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
                                    val lp = win.attributes
                                    lp.screenBrightness = newBrightness
                                    win.attributes = lp
                                    gestureHudType = "brightness"
                                    gestureHudValue = newBrightness
                                    gestureHudTimestamp = System.currentTimeMillis()
                                }
                            } else {
                                // Audio Volume adjustment
                                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val delta = if (dragAmount < -10) 1 else if (dragAmount > 10) -1 else 0
                                if (delta != 0) {
                                    val newVol = (currentVol + delta).coerceIn(0, maxAudioVolume)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                    gestureHudType = "volume"
                                    gestureHudValue = newVol.toFloat() / maxAudioVolume.toFloat()
                                    gestureHudTimestamp = System.currentTimeMillis()
                                }
                            }
                        }
                    )
                }
        )

        // 2. Loading Stream State (Clean, centered card without overlapping play button)
        if (isLoadingStream && streamError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = CrunchyOrange,
                        modifier = Modifier.size(50.dp),
                        strokeWidth = 3.5.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Menghubungkan Episode $currentEpisode...",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = anime.title,
                        color = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. Mid-playback Rebuffering Spinner (When controls are hidden)
        if (!isLoadingStream && isBuffering && streamError == null && !areControlsVisible) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = CrunchyOrange,
                    modifier = Modifier.size(50.dp),
                    strokeWidth = 3.5.dp
                )
            }
        }

        // 4. Double-Tap Seek Visual Feedback Pill
        if (seekFeedback != null) {
            val isFwd = seekFeedback!!.isForward
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
                contentAlignment = if (isFwd) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isFwd) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = null,
                                tint = CrunchyOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "-10 Detik",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "+10 Detik",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = null,
                                tint = CrunchyOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Brightness & Volume Swipe Gesture HUD
        if (gestureHudType != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val icon = if (gestureHudType == "brightness") {
                            Icons.Default.BrightnessMedium
                        } else {
                            if (gestureHudValue > 0.5f) Icons.Default.VolumeUp
                            else if (gestureHudValue > 0f) Icons.Default.VolumeDown
                            else Icons.Default.VolumeMute
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = CrunchyOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        LinearProgressIndicator(
                            progress = { gestureHudValue },
                            color = CrunchyOrange,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier
                                .width(90.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Text(
                            text = "${(gestureHudValue * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 6. Error Overlay with Retry Button
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OrangeButton(
                            text = "Coba Lagi",
                            icon = Icons.Default.Refresh,
                            onClick = { loadStream() }
                        )
                        androidx.compose.material3.OutlinedButton(
                            onClick = { showReportSheet = true },
                            modifier = Modifier.height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Laporkan Masalah", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // 7. AniSkip Floating Pill Button (Lewati Intro / Outro)
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
                    .padding(end = 24.dp, bottom = 86.dp)
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
                    text = if (showSkipIntro) "Lewati Pembuka >>" else "Lewati Penutup >>",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 8. Custom Crunchyroll Controls Overlay (HUD)
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
                // Top Header Controls
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = anime.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Episode $currentEpisode dari $totalEpisodes",
                            color = CrunchyOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Episode selector button
                    IconButton(onClick = { showEpisodeSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "Daftar Episode",
                            tint = Color.White
                        )
                    }

                    // Subtitles selector button
                    IconButton(onClick = { showSubtitleSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "Subtitle",
                            tint = Color.White
                        )
                    }

                    // Speed selector button
                    IconButton(onClick = { showSpeedSheet = true }) {
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

                // Center Play/Pause & 10s Seek Controls (Only when not loading stream)
                if (!isLoadingStream && streamError == null) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                exoPlayer.seekTo(target)
                                seekFeedback = SeekFeedback(isForward = false, timestamp = System.currentTimeMillis())
                            },
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Mundur 10 Detik",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        // Center Action Button: If rebuffering mid-stream, show spinner; otherwise show Play/Pause
                        if (isBuffering) {
                            Box(
                                modifier = Modifier.size(68.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = CrunchyOrange,
                                    modifier = Modifier.size(46.dp),
                                    strokeWidth = 3.5.dp
                                )
                            }
                        } else {
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
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(target)
                                seekFeedback = SeekFeedback(isForward = true, timestamp = System.currentTimeMillis())
                            },
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Maju 10 Detik",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
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
                        .padding(horizontal = 18.dp, vertical = 10.dp)
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

                        // Next Episode Button in Bottom Bar (If not last episode)
                        if (currentEpisode < totalEpisodes) {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    currentEpisode += 1
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Episode Selanjutnya",
                                    tint = CrunchyOrange,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 9. Episode Picker Modal Bottom Sheet
        if (showEpisodeSheet) {
            ModalBottomSheet(
                onDismissRequest = { showEpisodeSheet = false },
                containerColor = DarkSurface,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pilih Episode",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Episode $currentEpisode dari $totalEpisodes",
                            fontSize = 12.sp,
                            color = CrunchyOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 64.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(totalEpisodes) { idx ->
                            val epNum = idx + 1
                            val isCurrent = epNum == currentEpisode
                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) CrunchyOrange else DarkSurfaceVariant)
                                    .clickable {
                                        if (epNum != currentEpisode) {
                                            currentEpisode = epNum
                                            showEpisodeSheet = false
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$epNum",
                                    color = if (isCurrent) Color.White else TextPrimary,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // 10. Subtitle Selector Modal Bottom Sheet
        if (showSubtitleSheet) {
            val subs = resolvedStream?.subtitles ?: emptyList()
            ModalBottomSheet(
                onDismissRequest = { showSubtitleSheet = false },
                containerColor = DarkSurface,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = "Pilih Takarir (Subtitle)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Column {
                        subs.forEachIndexed { idx, track ->
                            val isSelected = selectedSubtitleIndex == idx
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) DarkSurfaceVariant else Color.Transparent)
                                    .clickable {
                                        selectedSubtitleIndex = idx
                                        showSubtitleSheet = false
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
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = track.label,
                                    color = if (isSelected) CrunchyOrange else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Aktif",
                                        tint = CrunchyOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // 11. Playback Speed Selector Modal Bottom Sheet
        if (showSpeedSheet) {
            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            ModalBottomSheet(
                onDismissRequest = { showSpeedSheet = false },
                containerColor = DarkSurface,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = "Kecepatan Putar",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Column {
                        speeds.forEach { spd ->
                            val isSelected = selectedSpeed == spd
                            val label = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) DarkSurfaceVariant else Color.Transparent)
                                    .clickable {
                                        selectedSpeed = spd
                                        exoPlayer.playbackParameters = PlaybackParameters(spd)
                                        showSpeedSheet = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) CrunchyOrange else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Dipilih",
                                        tint = CrunchyOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
    if (showReportSheet) {
        ReportIssueBottomSheet(
            onDismissRequest = { showReportSheet = false },
            initialCategory = "Video Rusak / Buffer",
            animeTitle = anime.title,
            episodeNumber = currentEpisode,
            errorMessage = streamError
        )
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
