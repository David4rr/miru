package id.miru.app.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkSurfaceVariant
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary

data class EpisodeChunk(
    val index: Int,
    val start: Int,
    val end: Int,
    val label: String
)

fun calculateEpisodeChunks(totalEpisodes: Int, chunkSize: Int = 100): List<EpisodeChunk> {
    if (totalEpisodes <= 0) return emptyList()
    val chunks = mutableListOf<EpisodeChunk>()
    var start = 1
    var idx = 0
    while (start <= totalEpisodes) {
        val end = (start + chunkSize - 1).coerceAtMost(totalEpisodes)
        chunks.add(EpisodeChunk(index = idx, start = start, end = end, label = "$start - $end"))
        start = end + 1
        idx++
    }
    return chunks
}

fun findChunkIndexForEpisode(episode: Int, chunkSize: Int = 100): Int {
    return ((episode - 1).coerceAtLeast(0) / chunkSize)
}

@Composable
fun DetailEpisodeChunkSelector(
    chunks: List<EpisodeChunk>,
    selectedChunkIndex: Int,
    onChunkSelected: (EpisodeChunk) -> Unit,
    modifier: Modifier = Modifier
) {
    if (chunks.size <= 1) return

    val listState = rememberLazyListState()

    LaunchedEffect(selectedChunkIndex) {
        val safeIdx = selectedChunkIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
        listState.animateScrollToItem(safeIdx)
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(chunks, key = { _, chunk -> chunk.index }) { idx, chunk ->
            val isSelected = idx == selectedChunkIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) CrunchyOrange else DarkSurfaceVariant)
                    .clickable { onChunkSelected(chunk) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chunk.label,
                    fontSize = 12.sp,
                    color = if (isSelected) Color.White else TextMuted,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
