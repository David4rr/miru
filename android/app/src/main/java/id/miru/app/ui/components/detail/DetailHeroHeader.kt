package id.miru.app.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.miru.app.ui.util.ImageRequests
import id.miru.app.data.AnimeCard
import id.miru.app.data.AudioMode
import id.miru.app.ui.components.OrangeButton
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkBackground
import id.miru.app.ui.theme.DarkSurfaceVariant
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailHeroHeader(
    anime: AnimeCard,
    resumeEpisode: Int,
    isUpcoming: Boolean,
    onPlayClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    parallaxOffset: Float = 0f,
    isContinueWatch: Boolean = false,
    audioMode: AudioMode = AudioMode.SUB,
    onAudioModeChange: (AudioMode) -> Unit = {},
    hasDub: Boolean = true
) {
    val context = LocalContext.current
    val imageRequest = remember(anime.banner, anime.poster) {
        ImageRequests.heroBanner(context, anime.banner ?: anime.poster)
    }
    val scrimBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, DarkBackground.copy(alpha = 0.6f), DarkBackground),
            startY = 60f
        )
    }
    val genreShape = remember { RoundedCornerShape(6.dp) }
    val genreBorder = remember { Color(0xFF26262B) }

    Box(modifier = modifier.fillMaxWidth().height(270.dp).clipToBounds()) {
        AsyncImage(
            model = imageRequest,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.15f
                    scaleY = 1.15f
                    translationY = parallaxOffset * 0.5f
                }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimBrush)
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = anime.title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary, lineHeight = 28.sp)
            anime.titleEnglish?.let { eng ->
                if (eng != anime.title) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = eng, fontSize = 13.sp, color = TextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (anime.score != null && anime.score > 0.0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = CrunchyOrange, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = anime.displayScore, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
            Text(
                text = anime.displayStatus,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (anime.isNotYetReleased) Color(0xFFFFA726) else CrunchyOrange
            )
            anime.seasonYear?.let { yr ->
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = yr.toString(), fontSize = 12.sp, color = TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            anime.displayGenres.forEach { g ->
                Box(
                    modifier = Modifier
                        .clip(genreShape)
                        .background(DarkSurfaceVariant)
                        .border(1.dp, genreBorder, genreShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = g, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (!isUpcoming) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Audio: ${audioMode.label}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                DetailDubSubToggle(currentMode = audioMode, onModeSelected = onAudioModeChange, hasDub = hasDub)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        if (isUpcoming) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1A14))
                    .border(1.dp, Color(0xFF382E1E), RoundedCornerShape(8.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Akan Datang (Belum Tayang)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFA726))
            }
        } else {
            OrangeButton(
                text = if (isContinueWatch || resumeEpisode > 1) "Lanjutkan Menonton (Episode $resumeEpisode)" else "Mulai Menonton (Episode 1)",
                onClick = { onPlayClick(resumeEpisode) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        }
    }
}
