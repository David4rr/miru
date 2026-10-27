package id.tatap.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tatap.app.data.AnimeCard
import id.tatap.app.data.ApiClient
import id.tatap.app.data.HomeCatalog
import id.tatap.app.data.WatchHistoryItem
import id.tatap.app.ui.components.AnimePosterCard
import id.tatap.app.ui.components.ContinueWatchingCard
import id.tatap.app.ui.components.HeroBannerCarousel
import id.tatap.app.ui.components.OrangeButton
import id.tatap.app.ui.components.SectionHeader
import id.tatap.app.ui.theme.CrunchyOrange
import id.tatap.app.ui.theme.DarkBackground
import id.tatap.app.ui.theme.TextMuted
import id.tatap.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch
@Composable
fun HomeScreen(
    onAnimeClick: (AnimeCard) -> Unit,
    onContinueWatchClick: (WatchHistoryItem) -> Unit,
    onSearchClick: () -> Unit,
    onScheduleClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var catalog by remember { mutableStateOf<HomeCatalog?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadData() {
        scope.launch {
            isLoading = true
            errorMessage = null
            ApiClient.getHomeCatalog()
                .onSuccess {
                    catalog = it
                    isLoading = false
                }
                .onFailure {
                    errorMessage = it.localizedMessage ?: "Gagal memuat katalog anime"
                    isLoading = false
                }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when {
            isLoading && catalog == null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = CrunchyOrange, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Memuat anime terbaru...",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }

            errorMessage != null && catalog == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Koneksi Bermasalah",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Tidak dapat menghubungi server lokal",
                        fontSize = 13.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OrangeButton(
                        text = "Coba Lagi",
                        icon = Icons.Default.Refresh,
                        onClick = { loadData() }
                    )
                }
            }

            catalog != null -> {
                val cat = catalog!!

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Top App Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "TATAP",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 1.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = onScheduleClick) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Jadwal Rilis",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { loadData() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Muat Ulang",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = onSearchClick) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Cari",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    // 1. Hero Banner Carousel
                    if (cat.hero.isNotEmpty()) {
                        item {
                            HeroBannerCarousel(
                                items = cat.hero,
                                onItemClick = onAnimeClick,
                                onWatchClick = onAnimeClick
                            )
                        }
                    }

                    // 2. Lanjutkan Menonton Swimlane
                    if (cat.continueWatching.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            SectionHeader(title = "Lanjutkan Menonton")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(cat.continueWatching) { item ->
                                    ContinueWatchingCard(
                                        item = item,
                                        onClick = onContinueWatchClick
                                    )
                                }
                            }
                        }
                    }
                    // 3. Rilis Hari Ini (Airing Today)
                    if (cat.airingToday.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            SectionHeader(
                                title = "Rilis Hari Ini",
                                onSeeAllClick = onScheduleClick
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(cat.airingToday) { scheduleItem ->
                                    AnimePosterCard(
                                        anime = scheduleItem.anime,
                                        onClick = onAnimeClick,
                                        customBadge = "Ep ${scheduleItem.episode} • ${scheduleItem.formattedTime}"
                                    )
                                }
                            }
                        }
                    }


                    // 3. Populer Musim Ini Swimlane
                    if (cat.seasonal.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            SectionHeader(title = "Populer Musim Ini")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(cat.seasonal) { anime ->
                                    AnimePosterCard(
                                        anime = anime,
                                        onClick = onAnimeClick
                                    )
                                }
                            }
                        }
                    }

                    // 4. Sedang Tren / Top Airing Swimlane
                    if (cat.topAiring.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            SectionHeader(title = "Sedang Tren di Indonesia")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(cat.topAiring) { index, anime ->
                                    AnimePosterCard(
                                        anime = anime,
                                        onClick = onAnimeClick,
                                        rank = index + 1
                                    )
                                }
                            }
                        }
                    }
                    if (cat.topRated.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            SectionHeader(title = "Paling Berperingkat Tinggi")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(cat.topRated) { anime ->
                                    AnimePosterCard(
                                        anime = anime,
                                        onClick = onAnimeClick
                                    )
                                }
                            }
                        }
                    }

                    // 6. Akan Datang Mendatang (Upcoming)
                    if (cat.upcoming.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            SectionHeader(title = "Akan Datang Mendatang")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(cat.upcoming) { anime ->
                                    AnimePosterCard(
                                        anime = anime,
                                        onClick = onAnimeClick
                                    )
                                }
                            }
                        }
                    }

                    // 7. Favorit Sepanjang Masa (All-Time Popular)
                    if (cat.allTimePopular.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            SectionHeader(title = "Favorit Sepanjang Masa")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(cat.allTimePopular) { anime ->
                                    AnimePosterCard(
                                        anime = anime,
                                        onClick = onAnimeClick
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
