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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.VideoVariant
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkBackground
import id.miru.app.ui.theme.DarkSurface
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerQualitySheet(
    variants: List<VideoVariant>,
    selectedVariant: VideoVariant?,
    onSelectVariant: (VideoVariant?) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = DarkBackground,
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HighQuality,
                    contentDescription = null,
                    tint = CrunchyOrange,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "Kualitas Video", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Pilih resolusi stream atau biarkan adaptif", color = TextMuted, fontSize = 12.sp)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                item {
                    QualityOptionItem(
                        title = "Otomatis (ABR)",
                        badge = "Rekomendasi",
                        isSelected = selectedVariant == null,
                        onClick = { onSelectVariant(null); onDismissRequest() }
                    )
                }

                items(variants, key = { "${it.width}x${it.height}_${it.bandwidth}" }) { variant ->
                    QualityOptionItem(
                        title = formatQualityLabel(variant),
                        badge = formatBandwidthLabel(variant.bandwidth),
                        isSelected = selectedVariant == variant,
                        onClick = { onSelectVariant(variant); onDismissRequest() }
                    )
                }
            }
        }
    }
}

private fun formatQualityLabel(variant: VideoVariant): String {
    return when {
        variant.height >= 1080 -> "1080p FHD"
        variant.height >= 720 -> "720p HD"
        variant.height >= 480 -> "480p SD"
        variant.height >= 360 -> "360p"
        variant.height > 0 -> "${variant.height}p"
        else -> "${variant.bandwidth / 1000} kbps"
    }
}

private fun formatBandwidthLabel(bandwidth: Long): String {
    val mbps = bandwidth.toDouble() / 1_000_000.0
    return if (mbps >= 1.0) String.format(java.util.Locale.US, "%.1f Mbps", mbps) else "${bandwidth / 1000} kbps"
}

@Composable
private fun QualityOptionItem(
    title: String,
    badge: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) CrunchyOrange else Color.Transparent
    val backgroundColor = if (isSelected) DarkSurface else DarkSurface.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = if (isSelected) CrunchyOrange else TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = badge, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (isSelected) {
            Icon(imageVector = Icons.Default.Check, contentDescription = "Selected", tint = CrunchyOrange, modifier = Modifier.size(18.dp))
        }
    }
}
