package id.miru.app.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.miru.app.ui.theme.BorderSubtle
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkSurface
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary

@Composable
fun DetailEpisodeList(
    totalEpisodes: Int,
    posterUrl: String,
    resumeEpisode: Int,
    currentProgress: Int,
    isUpcoming: Boolean,
    onEpisodeClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Daftar Episode",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isUpcoming || totalEpisodes == 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Episode belum dirilis. Anime ini masih dalam tahap produksi / jadwal mendatang.",
                    fontSize = 13.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
            }
        } else {
            Column {
                for (epNum in 1..totalEpisodes) {
                    val isPastWatched = epNum < resumeEpisode
                    val isInProgress = epNum == resumeEpisode && currentProgress in 1..84

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurface)
                            .clickable { onEpisodeClick(epNum) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(BorderSubtle)
                        ) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CrunchyOrange, modifier = Modifier.size(22.dp))
                            }
                            if (isInProgress) {
                                LinearProgressIndicator(
                                    progress = { (currentProgress.toFloat() / 100f).coerceIn(0f, 1f) },
                                    color = CrunchyOrange,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Episode $epNum", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(2.dp))
                            val epStatus = when {
                                isInProgress -> "Sedang Ditonton ($currentProgress%)"
                                isPastWatched -> "Sudah Ditonton • Takarir Indonesia"
                                else -> "Takarir Indonesia • HD 1080p"
                            }
                            Text(
                                text = epStatus,
                                fontSize = 11.sp,
                                color = if (isInProgress) CrunchyOrange else TextMuted
                            )
                        }

                        Icon(Icons.Default.PlayArrow, contentDescription = "Putar", tint = CrunchyOrange, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
