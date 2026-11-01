package id.miru.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.AnimeCard
import id.miru.app.data.ApiClient
import id.miru.app.data.CatalogBrowseParams
import id.miru.app.data.browseCatalog
import id.miru.app.ui.components.AnimePosterCard
import id.miru.app.ui.components.search.BrowseFilterBar
import id.miru.app.ui.components.search.BrowseFilterBottomSheet
import id.miru.app.ui.components.search.SearchTrendingSection
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkBackground
import id.miru.app.ui.theme.DarkSurface
import id.miru.app.ui.theme.TextMuted
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    onAnimeClick: (AnimeCard) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AnimeCard>>(emptyList()) }
    var browseParams by remember { mutableStateOf(CatalogBrowseParams()) }
    var isSearching by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery, browseParams) {
        val trimmed = searchQuery.trim()
        if (trimmed.isNotEmpty()) {
            isSearching = true
            delay(350)
            ApiClient.searchAnime(trimmed)
                .onSuccess { searchResults = it; isSearching = false }
                .onFailure { isSearching = false }
        } else if (browseParams.hasActiveFilters) {
            isSearching = true
            ApiClient.browseCatalog(browseParams)
                .onSuccess { searchResults = it; isSearching = false }
                .onFailure { isSearching = false }
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    if (showFilterSheet) {
        BrowseFilterBottomSheet(
            currentParams = browseParams,
            onApply = { browseParams = it },
            onDismissRequest = { showFilterSheet = false }
        )
    }

    Column(modifier = modifier.fillMaxSize().background(DarkBackground).padding(top = 16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari judul anime, karakter, genre...", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Cari", tint = if (searchQuery.isNotEmpty()) CrunchyOrange else TextMuted)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Hapus", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface,
                focusedBorderColor = CrunchyOrange, unfocusedBorderColor = Color(0xFF23252E),
                focusedTextColor = Color.White, unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        BrowseFilterBar(
            params = browseParams,
            onOpenFilters = { showFilterSheet = true },
            onUpdateParams = { browseParams = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CrunchyOrange)
                }
            }
            searchQuery.trim().isEmpty() && !browseParams.hasActiveFilters -> {
                SearchTrendingSection(onTagClick = { searchQuery = it })
            }
            searchResults.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Anime Tidak Ditemukan", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Tidak ada hasil untuk kriteria pencarian ini. Coba periksa filter atau kata kunci.",
                            fontSize = 13.sp, color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(searchResults, key = { it.id }) { anime ->
                            AnimePosterCard(anime = anime, onClick = onAnimeClick, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
