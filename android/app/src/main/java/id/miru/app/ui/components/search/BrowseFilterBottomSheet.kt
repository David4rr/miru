package id.miru.app.ui.components.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.ApiClient
import id.miru.app.data.CatalogBrowseParams
import id.miru.app.data.CatalogFilterOptions
import id.miru.app.data.CatalogSortOption
import id.miru.app.data.getCatalogFilters
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkBackground
import id.miru.app.ui.theme.DarkSurfaceVariant
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowseFilterBottomSheet(
    currentParams: CatalogBrowseParams,
    onApply: (CatalogBrowseParams) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filterOptions by remember { mutableStateOf(CatalogFilterOptions()) }

    var selectedGenre by remember(currentParams) { mutableStateOf(currentParams.genre) }
    var selectedFormat by remember(currentParams) { mutableStateOf(currentParams.format) }
    var selectedSeason by remember(currentParams) { mutableStateOf(currentParams.season) }
    var selectedYear by remember(currentParams) { mutableStateOf(currentParams.year) }
    var selectedStatus by remember(currentParams) { mutableStateOf(currentParams.status) }
    var selectedSort by remember(currentParams) { mutableStateOf(currentParams.sort ?: "POPULARITY_DESC") }

    LaunchedEffect(Unit) {
        ApiClient.getCatalogFilters().onSuccess { filterOptions = it }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = DarkBackground,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Filter Katalog", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                TextButton(onClick = {
                    selectedGenre = null; selectedFormat = null; selectedSeason = null
                    selectedYear = null; selectedStatus = null; selectedSort = "POPULARITY_DESC"
                }) {
                    Text(text = "Reset", color = CrunchyOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp)
            ) {
                FilterSection("Urutkan", filterOptions.sortOptions, selectedSort, { it.value }, { it.label }) {
                    selectedSort = it ?: "POPULARITY_DESC"
                }
                Spacer(modifier = Modifier.height(14.dp))
                FilterSection("Format", filterOptions.formats, selectedFormat, { it }, { it }) { selectedFormat = it }
                Spacer(modifier = Modifier.height(14.dp))
                FilterSection("Musim", filterOptions.seasons, selectedSeason, { it }, { it }) { selectedSeason = it }
                Spacer(modifier = Modifier.height(14.dp))
                FilterSection("Status", filterOptions.statuses, selectedStatus, { it }, { it }) { selectedStatus = it }
                Spacer(modifier = Modifier.height(14.dp))
                FilterSection("Tahun", filterOptions.years.take(12), selectedYear, { it }, { "$it" }) { selectedYear = it }
                Spacer(modifier = Modifier.height(14.dp))
                FilterSection("Genre", filterOptions.genres, selectedGenre, { it }, { it }) { selectedGenre = it }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    onApply(CatalogBrowseParams(selectedGenre, selectedSeason, selectedYear, selectedFormat, selectedStatus, selectedSort))
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CrunchyOrange),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(text = "Terapkan Filter", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T, K> FilterSection(
    title: String,
    items: List<T>,
    selectedKey: K?,
    keySelector: (T) -> K,
    labelSelector: (T) -> String,
    onSelect: (K?) -> Unit
) {
    Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val key = keySelector(item)
            val isSelected = selectedKey == key
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) CrunchyOrange else DarkSurfaceVariant)
                    .clickable { onSelect(if (isSelected) null else key) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = labelSelector(item),
                    fontSize = 12.sp,
                    color = if (isSelected) Color.White else TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
