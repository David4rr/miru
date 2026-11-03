package id.miru.app.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import id.miru.app.data.ApiClient
import id.miru.app.data.getSetting
import id.miru.app.data.saveSetting
import id.miru.app.ui.components.detail.DetailEpisodeChunkSelector
import id.miru.app.ui.components.detail.DetailSortOrderButton
import id.miru.app.ui.components.detail.calculateEpisodeChunks
import id.miru.app.ui.components.detail.findChunkIndexForEpisode
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkBackground
import id.miru.app.ui.theme.DarkSurface
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerEpisodeSheet(
    totalEpisodes: Int,
    currentEpisode: Int,
    onSelectEpisode: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var isAscending by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        ApiClient.getSetting("episode_sort_order", "asc").onSuccess { isAscending = it != "desc" }
    }

    val chunks = remember(totalEpisodes) { calculateEpisodeChunks(totalEpisodes) }
    var selectedChunkIndex by remember(totalEpisodes, currentEpisode) {
        mutableIntStateOf(findChunkIndexForEpisode(currentEpisode).coerceIn(0, (chunks.size - 1).coerceAtLeast(0)))
    }
    val itemShape = remember { RoundedCornerShape(8.dp) }
    val displayedEpisodes = remember(totalEpisodes, selectedChunkIndex, isAscending) {
        val range = if (chunks.isNotEmpty()) {
            val chunk = chunks.getOrNull(selectedChunkIndex) ?: chunks[0]
            chunk.start..chunk.end
        } else 1..totalEpisodes
        if (isAscending) range.toList() else range.reversed().toList()
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
                Column {
                    Text(text = "Pilih Episode", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "$totalEpisodes Episode", fontSize = 12.sp, color = TextMuted)
                }
                if (totalEpisodes > 0) {
                    DetailSortOrderButton(
                        isAscending = isAscending,
                        onToggleSortOrder = { asc ->
                            isAscending = asc
                            scope.launch { ApiClient.saveSetting("episode_sort_order", if (asc) "asc" else "desc") }
                        }
                    )
                }
            }

            if (chunks.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                DetailEpisodeChunkSelector(
                    chunks = chunks,
                    selectedChunkIndex = selectedChunkIndex,
                    onChunkSelected = { selectedChunkIndex = it.index }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (totalEpisodes == 0) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Belum ada episode yang dirilis.", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    items(
                        items = displayedEpisodes,
                        key = { it },
                        contentType = { "episode_grid_item" }
                    ) { ep ->
                        val isSelected = ep == currentEpisode
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(itemShape)
                                .background(if (isSelected) CrunchyOrange else DarkSurface)
                                .border(1.dp, if (isSelected) CrunchyOrange else Color(0xFF2E303B), itemShape)
                                .clickable {
                                    onSelectEpisode(ep)
                                    onDismissRequest()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$ep",
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
