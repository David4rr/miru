package id.miru.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.AnimeCard
import id.miru.app.data.ApiClient
import id.miru.app.data.BookmarkItem
import id.miru.app.data.WatchHistoryItem
import id.miru.app.ui.components.AnimePosterCard
import id.miru.app.ui.components.library.HistoryRowItem
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkBackground
import id.miru.app.ui.theme.DarkSurfaceVariant
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary
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

    fun refreshData() {
        scope.launch {
            ApiClient.getBookmarks().onSuccess { bookmarks = it }
            ApiClient.getHistory().onSuccess { history = it }
            isLoading = false
        }
    }

    LaunchedEffect(selectedTab) {
        refreshData()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(top = 16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Koleksi Saya", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Bookmark (${bookmarks.size})" to 0, "Riwayat (${history.size})" to 1).forEach { (label, idx) ->
                val isSelected = selectedTab == idx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CrunchyOrange else DarkSurfaceVariant)
                        .clickable { selectedTab = idx }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else TextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CrunchyOrange)
            }
        } else if (selectedTab == 0) {
            if (bookmarks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = TextMuted, modifier = Modifier.size(52.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Belum Ada Bookmark", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Simpan anime favoritmu agar mudah ditemukan di sini.", fontSize = 13.sp, color = TextMuted)
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
                        AnimePosterCard(anime = AnimeCard(id = 0, slug = item.slug, title = item.title, poster = item.poster, status = item.type), onClick = onAnimeClick)
                    }
                }
            }
        } else {
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(52.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Belum Ada Riwayat Nonton", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Tonton episode anime untuk melihat riwayat tayangan di sini.", fontSize = 13.sp, color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(history) { item ->
                        HistoryRowItem(
                            item = item,
                            onClick = { onHistoryClick(item) },
                            onDeleteClick = {
                                scope.launch {
                                    ApiClient.deleteHistory(item.slug)
                                    history = history.filter { it.slug != item.slug }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
