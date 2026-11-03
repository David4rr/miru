package id.miru.app.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.miru.app.ui.util.ImageRequests
import id.miru.app.data.AnimeEdition
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkSurface
import id.miru.app.ui.theme.DarkSurfaceVariant
import id.miru.app.ui.theme.TextMuted

/**
 * Ordered list of anime seasons & editions (S1 to SN) for franchise navigation.
 * Displays official chronological release order so users know where to start watching.
 * Strictly adheres to < 200 LOC limit.
 */
@Composable
fun DetailEditionsSection(
    editions: List<AnimeEdition>,
    onEditionClick: (AnimeEdition) -> Unit,
    modifier: Modifier = Modifier
) {
    if (editions.size <= 1) return

    val tvSeasonsCount = editions.count { it.format in listOf("TV", "TV_SHORT") }
    val countBadge = if (tvSeasonsCount > 1) "${editions.size} Edisi (S1–S$tvSeasonsCount)" else "${editions.size} Edisi"
    val context = LocalContext.current
    val cardShape = remember { RoundedCornerShape(10.dp) }
    val badgeShape = remember { RoundedCornerShape(4.dp) }
    val scrimBrush = remember { Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))) }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Urutan Edisi & Musim",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Panduan urutan menonton lengkap",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CrunchyOrange.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = countBadge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrunchyOrange
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(editions, key = { it.id }, contentType = { "edition_card" }) { edition ->
                val isSelected = edition.isCurrent
                val borderColor = if (isSelected) CrunchyOrange else Color(0xFF262830)
                val imageRequest = remember(edition.poster) { ImageRequests.poster(context, edition.poster) }

                Column(
                    modifier = Modifier
                        .width(135.dp)
                        .clip(cardShape)
                        .background(DarkSurfaceVariant)
                        .border(if (isSelected) 1.5.dp else 1.dp, borderColor, cardShape)
                        .clickable(enabled = !isSelected) { onEditionClick(edition) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                            .background(DarkSurface)
                    ) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = edition.displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .align(Alignment.BottomCenter)
                                .background(scrimBrush)
                        )

                        // Season / Edition badge (Top-Left)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(badgeShape)
                                .background(if (isSelected) CrunchyOrange else Color.Black.copy(alpha = 0.82f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isSelected) "${edition.editionLabel} • Aktif" else edition.editionLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        // Format / Year (Top-Right)
                        edition.seasonYear?.let { year ->
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clip(badgeShape)
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = year.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFDCDFEA)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = edition.displayTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) CrunchyOrange else Color.White,
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                        Text(
                            text = edition.displaySubtitle,
                            fontSize = 11.sp,
                            color = TextMuted,
                            minLines = 1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
