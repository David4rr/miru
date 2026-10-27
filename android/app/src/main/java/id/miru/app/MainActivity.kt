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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import id.miru.app.data.AnimeCard
import id.miru.app.data.AppRelease
import id.miru.app.data.UpdateManager
import id.miru.app.ui.components.UpdateAvailableBottomSheet
import id.miru.app.ui.components.UpdateBanner
import id.miru.app.ui.components.OledBottomNavBar
import id.miru.app.ui.screens.DetailScreen
import id.miru.app.ui.screens.HomeScreen
import id.miru.app.ui.screens.LibraryScreen
import id.miru.app.ui.screens.PlayerScreen
import id.miru.app.ui.screens.SearchScreen
import id.miru.app.ui.screens.ScheduleScreen
import id.miru.app.ui.screens.SettingsScreen
import id.miru.app.ui.screens.SplashScreen
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    preferredMinDisplayRefreshRate = m.refreshRate
                    preferredMaxDisplayRefreshRate = m.refreshRate
                }
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
    LaunchedEffect(screenMode) {
        if (screenMode != 3) {
            (context as? Activity)?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(act.window, act.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
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
            3 -> {
                val (anime, ep) = activePlayingSession!!
                PlayerScreen(anime = anime, episode = ep, onBackClick = { activePlayingSession = null })
            }
            2 -> {
                BackHandler { selectedAnime = null }
                DetailScreen(anime = selectedAnime!!, onBackClick = { selectedAnime = null }, onPlayEpisodeClick = { a, ep -> activePlayingSession = Pair(a, ep) })
            }
            1 -> {
                BackHandler { showScheduleScreen = false }
                ScheduleScreen(onAnimeClick = { selectedAnime = it }, onBackClick = { showScheduleScreen = false })
            }
            else -> {
                Scaffold(
                    containerColor = DarkOled,
                    bottomBar = {
                        OledBottomNavBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                    }
                ) { innerPadding ->
                    if (showUpdateSheet && availableUpdate != null) {
                        UpdateAvailableBottomSheet(
                            release = availableUpdate!!,
                            onDismissRequest = { showUpdateSheet = false }
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkOled)
                            .padding(innerPadding)
                    ) {
                        if (availableUpdate != null && !isUpdateBannerDismissed) {
                            UpdateBanner(
                                release = availableUpdate!!,
                                onViewUpdateClick = { showUpdateSheet = true },
                                onDismissClick = { isUpdateBannerDismissed = true }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = {
                                    fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                                },
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
                }
            }
        }
    }
}
