package id.miru.app.ui.components.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.CatalogBrowseParams
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkSurfaceVariant
import id.miru.app.ui.theme.TextPrimary

@Composable
fun BrowseFilterBar(
    params: CatalogBrowseParams,
    onOpenFilters: () -> Unit,
    onUpdateParams: (CatalogBrowseParams) -> Unit,
    modifier: Modifier = Modifier
) {
    val chipShape = remember { RoundedCornerShape(8.dp) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        item(key = "filter_btn", contentType = "filter_button") {
            val hasActive = params.hasActiveFilters
            Row(
                modifier = Modifier
                    .clip(chipShape)
                    .background(if (hasActive) CrunchyOrange else DarkSurfaceVariant)
                    .clickable(onClick = onOpenFilters)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filter",
                    tint = if (hasActive) Color.White else CrunchyOrange,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (hasActive) "Filter Aktif" else "Filter Katalog",
                    fontSize = 12.sp,
                    color = if (hasActive) Color.White else TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (params.genre != null) {
            item(key = "filter_genre", contentType = "active_chip") {
                ActiveFilterChip(label = "Genre: ${params.genre}") {
                    onUpdateParams(params.copy(genre = null))
                }
            }
        }
        if (params.format != null) {
            item(key = "filter_format", contentType = "active_chip") {
                ActiveFilterChip(label = "Format: ${params.format}") {
                    onUpdateParams(params.copy(format = null))
                }
            }
        }
        if (params.season != null) {
            item(key = "filter_season", contentType = "active_chip") {
                ActiveFilterChip(label = "Musim: ${params.season}") {
                    onUpdateParams(params.copy(season = null))
                }
            }
        }
        if (params.year != null) {
            item(key = "filter_year", contentType = "active_chip") {
                ActiveFilterChip(label = "Tahun: ${params.year}") {
                    onUpdateParams(params.copy(year = null))
                }
            }
        }
        if (params.status != null) {
            item(key = "filter_status", contentType = "active_chip") {
                ActiveFilterChip(label = "Status: ${params.status}") {
                    onUpdateParams(params.copy(status = null))
                }
            }
        }

        if (!params.hasActiveFilters) {
            items(POPULAR_GENRES, key = { "genre_$it" }, contentType = { "genre_chip" }) { genre ->
                Box(
                    modifier = Modifier
                        .clip(chipShape)
                        .background(DarkSurfaceVariant)
                        .clickable { onUpdateParams(params.copy(genre = genre)) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(text = genre, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ActiveFilterChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .clickable(onClick = onRemove)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = CrunchyOrange, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(Icons.Default.Close, contentDescription = "Hapus filter", tint = CrunchyOrange, modifier = Modifier.size(12.dp))
    }
}
