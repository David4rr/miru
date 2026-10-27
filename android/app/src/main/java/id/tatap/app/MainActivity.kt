package id.tatap.app

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
import id.tatap.app.data.AnimeCard
import id.tatap.app.data.AppRelease
import id.tatap.app.data.UpdateManager
import id.tatap.app.ui.components.UpdateAvailableBottomSheet
import id.tatap.app.ui.components.UpdateBanner
import id.tatap.app.ui.screens.DetailScreen
import id.tatap.app.ui.screens.HomeScreen
import id.tatap.app.ui.screens.LibraryScreen
import id.tatap.app.ui.screens.PlayerScreen
import id.tatap.app.ui.screens.SearchScreen
import id.tatap.app.ui.screens.ScheduleScreen
import id.tatap.app.ui.screens.SettingsScreen
import id.tatap.app.ui.theme.CrunchyrollOrange
import id.tatap.app.ui.theme.DarkOled
import id.tatap.app.ui.theme.SurfaceDark
import id.tatap.app.ui.theme.TatapTheme
import id.tatap.app.ui.theme.TextMuted

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TatapTheme {
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
                bottomBar = {
                    NavigationBar(
                        containerColor = SurfaceDark,
                        contentColor = Color.White
                    ) {
                        val tabs = listOf(
                            Triple("Beranda", Icons.Default.Home, 0),
                            Triple("Cari", Icons.Default.Search, 1),
                            Triple("Koleksi", Icons.Default.VideoLibrary, 2),
                            Triple("Pengaturan", Icons.Default.Settings, 3)
                        )

                        tabs.forEach { (title, icon, index) ->
                            NavigationBarItem(
                                icon = { Icon(icon, contentDescription = title) },
                                label = { Text(title) },
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CrunchyrollOrange,
                                    selectedTextColor = CrunchyrollOrange,
                                    indicatorColor = SurfaceDark,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
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
                            onSearchClick = { selectedTab = 1 },
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
