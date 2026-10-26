package id.tatap.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.tatap.app.data.AnimeCard
import id.tatap.app.data.ApiClient
import id.tatap.app.data.BookmarkItem
import id.tatap.app.data.WatchHistoryItem
import id.tatap.app.ui.components.AnimePosterCard
import id.tatap.app.ui.theme.CrunchyOrange
import id.tatap.app.ui.theme.DarkBackground
import id.tatap.app.ui.theme.DarkSurface
import id.tatap.app.ui.theme.DarkSurfaceVariant
import id.tatap.app.ui.theme.TextMuted
import id.tatap.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    onAnimeClick: (AnimeCard) -> Unit,
    onHistoryClick: (WatchHistoryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var bookmarks by remember { mutableStateOf<List<BookmarkItem>>(emptyList()) }
    var history by remember { mutableStateOf<List<WatchHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun loadData() {
        scope.launch {
            isLoading = true
            ApiClient.getBookmarks().onSuccess { bookmarks = it }
            ApiClient.getHistory().onSuccess { history = it }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Screen Title Header
        Text(
            text = "Koleksi Saya",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        // Crunchyroll Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkBackground,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CrunchyOrange,
                    height = 3.dp
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = if (selectedTab == 0) CrunchyOrange else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tersimpan (${bookmarks.size})",
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 0) Color.White else TextMuted
                        )
                    }
                }
            )

            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = if (selectedTab == 1) CrunchyOrange else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Riwayat (${history.size})",
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 1) Color.White else TextMuted
                        )
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CrunchyOrange)
            }
        } else {
            when (selectedTab) {
                0 -> {
                    // Bookmarks Grid
                    if (bookmarks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Belum Ada Koleksi Tersimpan",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tekan tombol bookmark pada anime favoritmu untuk menyimpannya di sini.",
                                    fontSize = 13.sp,
                                    color = TextMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(bookmarks) { item ->
                                val card = AnimeCard(
                                    id = 0,
                                    slug = item.slug,
                                    title = item.title,
                                    poster = item.poster,
                                    score = item.score,
                                    status = item.type
                                )
                                AnimePosterCard(
                                    anime = card,
                                    onClick = onAnimeClick,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // Watch History List
                    if (history.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Belum Ada Riwayat Tontonan",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Anime yang kamu tonton akan otomatis tersimpan di sini agar kamu bisa melanjutkannya kapan saja.",
                                    fontSize = 13.sp,
                                    color = TextMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(history) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurface)
                                        .clickable { onHistoryClick(item) }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Thumbnail with Progress Indicator
                                    Box(
                                        modifier = Modifier
                                            .size(width = 96.dp, height = 58.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkSurfaceVariant)
                                    ) {
                                        AsyncImage(
                                            model = item.poster,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        val progressFactor = (item.progress.coerceIn(0, 100)) / 100f
                                        LinearProgressIndicator(
                                            progress = { progressFactor },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .align(Alignment.BottomCenter),
                                            color = CrunchyOrange,
                                            trackColor = Color.White.copy(alpha = 0.3f),
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Episode ${item.episode}",
                                            fontSize = 12.sp,
                                            color = CrunchyOrange,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (!item.playedAt.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = item.playedAt,
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                ApiClient.deleteHistory(item.slug)
                                                history = history.filter { it.slug != item.slug }
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Hapus Riwayat",
                                            tint = TextMuted,
                                            modifier = Modifier.size(20.dp)
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
}
