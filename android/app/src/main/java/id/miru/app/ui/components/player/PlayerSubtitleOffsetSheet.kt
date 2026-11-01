package id.miru.app.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkBackground
import id.miru.app.ui.theme.DarkSurface
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSubtitleOffsetSheet(
    slug: String = "",
    episode: Int = 1,
    initialOffsetMs: Int = 0,
    onOffsetChanged: (Int) -> Unit = {},
    onSaveOffset: (Int) -> Unit = {},
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var currentOffsetMs by remember { mutableIntStateOf(initialOffsetMs) }

    LaunchedEffect(slug, episode) {
        if (slug.isNotBlank()) {
            ApiClientSubtitles.getSubtitleOffset(slug, episode).onSuccess { loaded ->
                if (loaded != 0) { currentOffsetMs = loaded; onOffsetChanged(loaded) }
            }
        }
    }

    fun updateOffset(target: Int) {
        val rounded = ((target.coerceIn(-10000, 10000)) / 50) * 50
        currentOffsetMs = rounded; onOffsetChanged(rounded)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest, sheetState = sheetState,
        containerColor = DarkSurface, scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AvTimer, contentDescription = null, tint = CrunchyOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sinkronisasi Waktu Subtitle", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                val secStr = String.format("%.2f s", currentOffsetMs / 1000f)
                Text(if (currentOffsetMs > 0) "+$secStr" else secStr, color = CrunchyOrange, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = currentOffsetMs.toFloat(),
                onValueChange = { newVal ->
                    val rounded = ((newVal / 50f).roundToInt()) * 50
                    currentOffsetMs = rounded; onOffsetChanged(rounded)
                },
                valueRange = -10000f..10000f, steps = 399,
                colors = SliderDefaults.colors(thumbColor = CrunchyOrange, activeTrackColor = CrunchyOrange, inactiveTrackColor = Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("-10.0 s", color = TextMuted, fontSize = 11.sp)
                Text("0.0 s", color = TextMuted, fontSize = 11.sp)
                Text("+10.0 s", color = TextMuted, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OffsetNudgeBtn("-500ms", modifier = Modifier.weight(1f)) { updateOffset(currentOffsetMs - 500) }
                OffsetNudgeBtn("-100ms", modifier = Modifier.weight(1f)) { updateOffset(currentOffsetMs - 100) }
                OffsetNudgeBtn("Reset", isHighlight = true, modifier = Modifier.weight(1f)) { updateOffset(0) }
                OffsetNudgeBtn("+100ms", modifier = Modifier.weight(1f)) { updateOffset(currentOffsetMs + 100) }
                OffsetNudgeBtn("+500ms", modifier = Modifier.weight(1f)) { updateOffset(currentOffsetMs + 500) }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = {
                    scope.launch {
                        if (slug.isNotBlank()) ApiClientSubtitles.saveSubtitleOffset(slug, episode, currentOffsetMs)
                        onSaveOffset(currentOffsetMs); onDismissRequest()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CrunchyOrange)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simpan Offset Episode Ini", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OffsetNudgeBtn(label: String, modifier: Modifier = Modifier, isHighlight: Boolean = false, onClick: () -> Unit) {
    val bg = if (isHighlight) CrunchyOrange.copy(alpha = 0.2f) else DarkBackground
    val border = if (isHighlight) CrunchyOrange else Color.White.copy(alpha = 0.1f)
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(bg).border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable { onClick() }.padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (isHighlight) CrunchyOrange else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
