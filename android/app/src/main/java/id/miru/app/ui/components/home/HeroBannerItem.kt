package id.miru.app.ui.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.miru.app.data.AnimeCard
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkBackground
import id.miru.app.ui.theme.DarkSurface
import id.miru.app.ui.theme.TextPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeroBannerItem(
    anime: AnimeCard,
    onItemClick: (AnimeCard) -> Unit,
    onWatchClick: (AnimeCard) -> Unit,
    modifier: Modifier = Modifier,
    parallaxOffset: Float = 0f,
    parallaxOffsetProvider: (() -> Float)? = null,
    pageOffset: Float = 0f,
    absOffset: Float = 0f
) {
    val offsetProvider = parallaxOffsetProvider ?: { parallaxOffset }
    val cardShape = RoundedCornerShape(20.dp)
    val cardBorder = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.03f))
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            .graphicsLayer {
                scaleX = 1f - 0.08f * absOffset
                scaleY = 1f - 0.08f * absOffset
                alpha = 1f - 0.45f * absOffset
            }
            .clip(cardShape)
            .border(1.dp, cardBorder, cardShape)
            .clickable { onItemClick(anime) }
    ) {
        AsyncImage(
            model = anime.banner ?: anime.poster,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.05f
                scaleY = 1.05f
                translationX = pageOffset * 35f
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, DarkBackground.copy(alpha = 0.6f), DarkBackground.copy(alpha = 0.95f), DarkBackground)
                    )
                )
                .drawBehind {
                    val darkening = (offsetProvider() / 280f).coerceIn(0f, 0.75f)
                    if (darkening > 0f) drawRect(DarkBackground.copy(alpha = darkening))
                }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .graphicsLayer {
                    val scrollOffset = offsetProvider()
                    alpha = (1f - (scrollOffset / 200f)).coerceIn(0f, 1f)
                    translationY = -(scrollOffset * 0.15f).coerceIn(0f, 30f)
                }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (anime.score != null && anime.score > 0.0) {
                    val pillShape = RoundedCornerShape(6.dp)
                    Row(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.18f), pillShape)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = CrunchyOrange, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = anime.displayScore, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                val epShape = RoundedCornerShape(6.dp)
                Box(
                    modifier = Modifier
                        .clip(epShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), epShape)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(text = anime.displayEpisodes, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = anime.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (anime.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    anime.genres.take(3).forEach { genre ->
                        val genreShape = RoundedCornerShape(6.dp)
                        Box(
                            modifier = Modifier
                                .clip(genreShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), genreShape)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(text = genre, fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onWatchClick(anime) },
                    colors = ButtonDefaults.buttonColors(containerColor = CrunchyOrange),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Tonton Sekarang", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = { onItemClick(anime) },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.BookmarkBorder, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Detail", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}
