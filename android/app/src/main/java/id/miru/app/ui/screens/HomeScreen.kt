package id.miru.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.AnimeCard
import id.miru.app.data.ApiClient
import id.miru.app.data.HomeCatalog
import id.miru.app.data.WatchHistoryItem
import id.miru.app.ui.components.OrangeButton
import id.miru.app.ui.components.home.HomeTopHeader
import id.miru.app.ui.components.home.homeCatalogSwimlanes
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkBackground
import id.miru.app.ui.theme.TextMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAnimeClick: (AnimeCard) -> Unit,
    onContinueWatchClick: (WatchHistoryItem) -> Unit,
    onScheduleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var catalog by remember { mutableStateOf<HomeCatalog?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadData(showFullLoading: Boolean = false) {
        scope.launch {
            if (showFullLoading) isLoading = true
            errorMessage = null
            ApiClient.getHomeCatalog()
                .onSuccess {
                    catalog = it
                    isLoading = false
                    isRefreshing = false
                }
                .onFailure {
                    errorMessage = it.localizedMessage ?: "Gagal memuat katalog anime"
                    isLoading = false
                    isRefreshing = false
                }
        }
    }

    LaunchedEffect(Unit) {
        loadData(showFullLoading = true)
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
                    Text(text = "Memuat anime terbaru...", color = TextMuted, fontSize = 14.sp)
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
                    Text(text = "Koneksi Bermasalah", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorMessage ?: "Terjadi kesalahan", color = TextMuted, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OrangeButton(text = "Coba Lagi", onClick = { loadData(showFullLoading = true) })
                }
            }

            catalog != null -> {
                val pullRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    state = pullRefreshState,
                    onRefresh = {
                        isRefreshing = true
                        loadData(showFullLoading = false)
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 72.dp)
                    ) {
                        item {
                            HomeTopHeader(onScheduleClick = onScheduleClick)
                        }

                        catalog?.let { c ->
                            homeCatalogSwimlanes(
                                catalog = c,
                                onAnimeClick = onAnimeClick,
                                onContinueWatchClick = onContinueWatchClick,
                                onScheduleClick = onScheduleClick
                            )
                        }
                    }
                }
            }
        }
    }
}
