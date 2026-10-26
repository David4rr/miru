package id.tatap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import id.tatap.app.data.AnimeCard
import id.tatap.app.ui.screens.DetailScreen
import id.tatap.app.ui.screens.HomeScreen
import id.tatap.app.ui.screens.LibraryScreen
import id.tatap.app.ui.screens.SearchScreen
import id.tatap.app.ui.theme.CrunchyrollOrange
import id.tatap.app.ui.theme.DarkOled
import id.tatap.app.ui.theme.SurfaceDark
import id.tatap.app.ui.theme.TatapTheme
import id.tatap.app.ui.theme.TextMuted

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    // Intercept back button if DetailScreen is open
    BackHandler(enabled = selectedAnime != null) {
        selectedAnime = null
    }

    if (selectedAnime != null) {
        DetailScreen(
            anime = selectedAnime!!,
            onBackClick = { selectedAnime = null },
            onPlayEpisodeClick = { anime, ep ->
                // Handled in Phase 5 Media3 Player Screen
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = Color.White
                ) {
                    val tabs = listOf(
                        Triple("Beranda", Icons.Default.Home, 0),
                        Triple("Cari", Icons.Default.Search, 1),
                        Triple("Koleksi", Icons.Default.VideoLibrary, 2)
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkOled)
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> HomeScreen(
                        onAnimeClick = { selectedAnime = it },
                        onContinueWatchClick = { item ->
                            selectedAnime = AnimeCard(
                                id = 0,
                                slug = item.slug,
                                title = item.title,
                                poster = item.poster,
                                status = item.type
                            )
                        },
                        onSearchClick = { selectedTab = 1 }
                    )
                    1 -> SearchScreen(
                        onAnimeClick = { selectedAnime = it }
                    )
                    2 -> LibraryScreen(
                        onAnimeClick = { selectedAnime = it },
                        onHistoryClick = { item ->
                            selectedAnime = AnimeCard(
                                id = 0,
                                slug = item.slug,
                                title = item.title,
                                poster = item.poster,
                                status = item.type
                            )
                        }
                    )
                }
            }
        }
    }
}
