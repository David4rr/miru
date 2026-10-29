package id.miru.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import id.miru.app.data.AnimeCard
import id.miru.app.data.AppRelease
import id.miru.app.data.UpdateManager
import id.miru.app.ui.components.*
import android.widget.Toast
import id.miru.app.ui.screens.*
import id.miru.app.ui.theme.DarkOled
import id.miru.app.ui.theme.MiruTheme
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureHighRefreshRate()
        setContent {
            MiruTheme {
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) SplashScreen(onSplashFinished = { showSplash = false }) else MainApp()
            }
        }
    }

    private fun configureHighRefreshRate() {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.supportedModes?.maxByOrNull { it.refreshRate }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.supportedModes?.maxByOrNull { it.refreshRate }
        } else null
        mode?.let { m ->
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = m.modeId
                preferredRefreshRate = m.refreshRate
            }
        }
    }
}

@Composable
fun MainApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedAnime by remember { mutableStateOf<AnimeCard?>(null) }
    var activePlayingSession by remember { mutableStateOf<Pair<AnimeCard, Int>?>(null) }
    var availableUpdate by remember { mutableStateOf<AppRelease?>(null) }; var showUpdateSheet by remember { mutableStateOf(false) }
    var showScheduleScreen by remember { mutableStateOf(false) }; var isUpdateBannerDismissed by remember { mutableStateOf(false) }
    var isFabVisible by remember { mutableStateOf(true) }; var lastScrollTime by remember { mutableLongStateOf(0L) }
    var lastAnime by remember { mutableStateOf<AnimeCard?>(null) }; if (selectedAnime != null) lastAnime = selectedAnime
    var lastSession by remember { mutableStateOf<Pair<AnimeCard, Int>?>(null) }; if (activePlayingSession != null) lastSession = activePlayingSession
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y.absoluteValue > 3f) { lastScrollTime = System.currentTimeMillis(); isFabVisible = false }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(isFabVisible, lastScrollTime) { if (!isFabVisible) { delay(600); isFabVisible = true } }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        UpdateManager.checkLatestUpdate().getOrNull()?.let { if (it.isNewer) availableUpdate = it }
    }
    val screenMode = when {
        activePlayingSession != null -> 3
        selectedAnime != null -> 2
        showScheduleScreen -> 1
        else -> 0
    }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(screenMode, selectedTab) {
        lastBackPressTime = 0L
        if (screenMode != 3) {
            (context as? Activity)?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(act.window, act.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    BackHandler(enabled = screenMode == 0) {
        val now = System.currentTimeMillis()
        if (lastBackPressTime != 0L && now - lastBackPressTime < 2000L) {
            (context as? Activity)?.finish()
        } else {
            lastBackPressTime = now
            Toast.makeText(context, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
        }
    }
    AnimatedContent(
        targetState = screenMode,
        transitionSpec = {
            fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
        },
        label = "screenTransition"
    ) { mode ->
        when (mode) {
            3 -> (activePlayingSession ?: lastSession)?.let { (anime, ep) ->
                PlayerScreen(anime = anime, episode = ep, onBackClick = { activePlayingSession = null })
            }
            2 -> (selectedAnime ?: lastAnime)?.let { anime ->
                BackHandler { selectedAnime = null }
                DetailScreen(anime = anime, onBackClick = { selectedAnime = null }, onPlayEpisodeClick = { a, ep -> activePlayingSession = Pair(a, ep) })
            }
            1 -> {
                BackHandler { showScheduleScreen = false }
                ScheduleScreen(onAnimeClick = { selectedAnime = it }, onBackClick = { showScheduleScreen = false })
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
                    if (showUpdateSheet) {
                        availableUpdate?.let { rel ->
                            UpdateAvailableBottomSheet(release = rel, onDismissRequest = { showUpdateSheet = false })
                        }
                    }
                    Column(modifier = Modifier.fillMaxSize().background(DarkOled).statusBarsPadding()) {
                        if (!isUpdateBannerDismissed) {
                            availableUpdate?.let { rel ->
                                UpdateBanner(release = rel, onViewUpdateClick = { showUpdateSheet = true }, onDismissClick = { isUpdateBannerDismissed = true })
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                                label = "tabTransition",
                                modifier = Modifier.fillMaxSize()
                            ) { tab ->
                                when (tab) {
                                    0 -> HomeScreen(
                                        onAnimeClick = { selectedAnime = it },
                                        onContinueWatchClick = { item ->
                                            val card = AnimeCard(id = 0, slug = item.slug, title = item.title, poster = item.poster, status = item.type)
                                            activePlayingSession = Pair(card, item.episode)
                                        },
                                        onScheduleClick = { showScheduleScreen = true }
                                    )
                                    1 -> SearchScreen(onAnimeClick = { selectedAnime = it })
                                    2 -> LibraryScreen(
                                        onAnimeClick = { selectedAnime = it },
                                        onHistoryClick = { item ->
                                            val card = AnimeCard(id = 0, slug = item.slug, title = item.title, poster = item.poster, status = item.type)
                                            activePlayingSession = Pair(card, item.episode)
                                        }
                                    )
                                    3 -> SettingsScreen()
                                }
                            }
                        }
                    }
                    FloatingNavFab(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        isVisible = isFabVisible,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
