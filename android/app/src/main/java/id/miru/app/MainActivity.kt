package id.miru.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import id.miru.app.ui.theme.CrunchyrollOrange
import id.miru.app.ui.theme.DarkOled
import id.miru.app.ui.theme.SurfaceDark
import id.miru.app.ui.theme.MiruTheme
import id.miru.app.ui.theme.TextMuted

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiruTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedAnime by remember { mutableStateOf<AnimeCard?>(null) }
    var activePlayingSession by remember { mutableStateOf<Pair<AnimeCard, Int>?>(null) }

    var availableUpdate by remember { mutableStateOf<AppRelease?>(null) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    var showScheduleScreen by remember { mutableStateOf(false) }
    var isUpdateBannerDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val res = UpdateManager.checkLatestUpdate()
        res.getOrNull()?.let { rel ->
            if (rel.isNewer) {
                availableUpdate = rel
            }
        }
    }
    when {
        // 1. Fullscreen Video Player Mode (ExoPlayer Media3)
        activePlayingSession != null -> {
            val (anime, ep) = activePlayingSession!!
            PlayerScreen(
                anime = anime,
                episode = ep,
                onBackClick = { activePlayingSession = null }
            )
        }

        // 2. Anime Detail Screen Mode
        selectedAnime != null -> {
            BackHandler {
                selectedAnime = null
            }
            DetailScreen(
                anime = selectedAnime!!,
                onBackClick = { selectedAnime = null },
                onPlayEpisodeClick = { anime, ep ->
                    activePlayingSession = Pair(anime, ep)
                }
            )
        }
        // 3. Weekly Airing Schedule Screen Mode
        showScheduleScreen -> {
            BackHandler {
                showScheduleScreen = false
            }
            ScheduleScreen(
                onAnimeClick = { selectedAnime = it },
                onBackClick = { showScheduleScreen = false }
            )
        }


        // 3. Main Bottom Navigation Screens
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

                androidx.compose.foundation.layout.Column(
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
                        when (selectedTab) {
                        0 -> HomeScreen(
                            onAnimeClick = { selectedAnime = it },
                            onContinueWatchClick = { item ->
                                activePlayingSession = Pair(
                                    AnimeCard(
                                        id = 0,
                                        slug = item.slug,
                                        title = item.title,
                                        poster = item.poster,
                                        status = item.type
                                    ),
                                    item.episode
                                )
                            },
                            onScheduleClick = { showScheduleScreen = true }
                        )
                        1 -> SearchScreen(
                            onAnimeClick = { selectedAnime = it }
                        )
                        2 -> LibraryScreen(
                            onAnimeClick = { selectedAnime = it },
                            onHistoryClick = { item ->
                                activePlayingSession = Pair(
                                    AnimeCard(
                                        id = 0,
                                        slug = item.slug,
                                        title = item.title,
                                        poster = item.poster,
                                        status = item.type
                                    ),
                                    item.episode
                                )
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
