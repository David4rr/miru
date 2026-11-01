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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import id.miru.app.data.ApiClientSubtitles
import id.miru.app.data.SubtitleTrack
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkBackground
import id.miru.app.ui.theme.DarkSurface
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSubtitleSheet(
    subtitles: List<SubtitleTrack>,
    selectedSubtitle: SubtitleTrack?,
    animeTitle: String = "",
    episodeNum: Int = 1,
    onSelectSubtitle: (SubtitleTrack?) -> Unit,
    onOpenOffsetSheet: () -> Unit = {},
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var externalTracks by remember { mutableStateOf<List<SubtitleTrack>>(emptyList()) }
    var isLoadingExternal by remember { mutableStateOf(false) }

    fun fetchExternal() {
        if (isLoadingExternal || animeTitle.isBlank()) return
        isLoadingExternal = true
        scope.launch {
            val tosho = ApiClientSubtitles.fetchAnimeToshoSubtitles(null, animeTitle, episodeNum).getOrDefault(emptyList())
            val openSubs = ApiClientSubtitles.fetchOpenSubtitles(animeTitle, episodeNum).getOrDefault(emptyList())
            externalTracks = (tosho + openSubs).map { SubtitleTrack(it.label, it.lang, it.downloadUrl, false) }
            isLoadingExternal = false
        }
    }

    val baseSub = subtitles.find { it.lang == "en" } ?: subtitles.firstOrNull { !it.url.contains("mode=") }
    val smartTracks = remember(baseSub, subtitles) {
        if (baseSub == null) return@remember emptyList<SubtitleTrack>()
        val cleanUrl = baseSub.url.replace("[?&]mode=[^&]+".toRegex(), "")
        val delim = if (cleanUrl.contains("?")) "&" else "?"
        listOf(
            SubtitleTrack("Bahasa Indonesia (GTX+ Standar)", "id", "$cleanUrl${delim}mode=gtx"),
            SubtitleTrack("Bahasa Indonesia (AI Fansub BYOK)", "id", "$cleanUrl${delim}mode=ai"),
            SubtitleTrack("Bilingual (ID + EN)", "id", "$cleanUrl${delim}mode=bilingual")
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest, sheetState = sheetState, containerColor = DarkSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = CrunchyOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pilih Takarir", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(CrunchyOrange.copy(alpha = 0.15f))
                        .clickable { onDismissRequest(); onOpenOffsetSheet() }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AvTimer, contentDescription = null, tint = CrunchyOrange, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Atur Offset", color = CrunchyOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item {
                    SubtitleOptionItem("Nonaktifkan Subtitle", "OFF", selectedSubtitle == null) {
                        onSelectSubtitle(null); onDismissRequest()
                    }
                }
                if (smartTracks.isNotEmpty()) {
                    items(smartTracks, key = { it.url }) { track ->
                        val badge = if (track.url.contains("mode=ai")) "AI" else if (track.url.contains("mode=bilingual")) "DUAL" else "GTX+"
                        SubtitleOptionItem(track.label, badge, selectedSubtitle?.url == track.url) {
                            onSelectSubtitle(track); onDismissRequest()
                        }
                    }
                }
                items(subtitles, key = { it.url }) { track ->
                    if (!smartTracks.any { it.url == track.url }) {
                        SubtitleOptionItem(track.label, track.lang.uppercase(), selectedSubtitle?.url == track.url) {
                            onSelectSubtitle(track); onDismissRequest()
                        }
                    }
                }
                if (externalTracks.isNotEmpty()) {
                    items(externalTracks, key = { it.url }) { track ->
                        SubtitleOptionItem(track.label, "EXT", selectedSubtitle?.url == track.url) {
                            onSelectSubtitle(track); onDismissRequest()
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.05f))
                            .clickable { fetchExternal() }.padding(12.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isLoadingExternal) "Mencari..." else "Cari di AnimeTosho & OpenSubtitles", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleOptionItem(title: String, badge: String, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) CrunchyOrange else Color.White.copy(alpha = 0.1f)
    val bgColor = if (isSelected) CrunchyOrange.copy(alpha = 0.15f) else DarkBackground.copy(alpha = 0.6f)
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)).clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) CrunchyOrange else Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(badge, color = if (isSelected) Color.Black else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(title, color = if (isSelected) CrunchyOrange else TextPrimary, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
        }
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = "Aktif", tint = CrunchyOrange, modifier = Modifier.size(18.dp))
        }
    }
}
