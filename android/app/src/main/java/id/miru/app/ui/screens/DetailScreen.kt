package id.miru.app.ui.screens
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.miru.app.data.AnimeCard
import id.miru.app.data.ApiClient
import id.miru.app.data.BookmarkItem
import id.miru.app.data.WatchHistoryItem
import id.miru.app.ui.components.OrangeButton
import id.miru.app.ui.components.SectionHeader
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkBackground
import id.miru.app.ui.theme.DarkSurface
import id.miru.app.ui.theme.DarkSurfaceVariant
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    anime: AnimeCard,
    onBackClick: () -> Unit,
    onPlayEpisodeClick: (AnimeCard, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isBookmarked by remember { mutableStateOf(false) }
    var watchedHistory by remember { mutableStateOf<WatchHistoryItem?>(null) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }
    var translatedSynopsis by remember { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    var showOriginalSynopsis by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(anime.slug) {
        ApiClient.getBookmarks().onSuccess { list ->
            isBookmarked = list.any { it.slug == anime.slug }
        }
        ApiClient.getHistory().onSuccess { list ->
            watchedHistory = list.firstOrNull { it.slug == anime.slug }
        }
    }
    fun toggleBookmark() {
        scope.launch {
            if (isBookmarked) {
                ApiClient.removeBookmark(anime.slug).onSuccess {
                    isBookmarked = false
                }
            } else {
                val item = BookmarkItem(
                    slug = anime.slug,
                    title = anime.title,
                    poster = anime.poster,
                    type = anime.status ?: "ANIME",
                    sub = "SUB",
                    eps = anime.displayEpisodes,
                    score = anime.score ?: 0.0
                )
                ApiClient.addBookmark(item).onSuccess {
                    isBookmarked = true
                }
            }
        }
    }

    val isNotYetReleased = anime.isNotYetReleased
    val totalEpisodes = if (isNotYetReleased) {
        0
    } else {
        anime.episodesReleased ?: anime.episodes ?: 0
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 1. Hero Backdrop Header with Scrim
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp)
                ) {
                    val backdropUrl = anime.banner ?: anime.poster
                    AsyncImage(
                        model = backdropUrl,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient fading into DarkBackground
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        DarkBackground.copy(alpha = 0.5f),
                                        DarkBackground.copy(alpha = 0.8f),
                                        DarkBackground
                                    )
                                )
                            )
                    )

                    // Top Bar (Back & Bookmark) with statusBarsPadding to avoid camera hole / clock overlap
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = { toggleBookmark() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) CrunchyOrange else Color.White
                            )
                        }
                    }
                }
            }

            // 2. Poster & Metadata Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-40).dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Poster Card (2:3 aspect)
                    Box(
                        modifier = Modifier
                            .width(115.dp)
                            .height(165.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                    ) {
                        AsyncImage(
                            model = anime.poster,
                            contentDescription = anime.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Title & Quick Details
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = anime.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = CrunchyOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = anime.displayScore,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "•",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = anime.displayEpisodes,
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                        }

                        if (anime.status != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = anime.displayStatus,
                                fontSize = 12.sp,
                                color = CrunchyOrange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 3. CTA Play Button & Genres
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (isNotYetReleased) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = CrunchyOrange,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Akan Datang (Belum Tayang)",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Anime ini belum resmi rilis. Simpan ke Koleksi untuk menandainya.",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    } else if (totalEpisodes > 0) {
                        val resumeEp = watchedHistory?.episode ?: 1
                        val ctaText = if (watchedHistory != null) "Lanjutkan Menonton (Episode $resumeEp)" else "Mulai Menonton (Episode 1)"
                        OrangeButton(
                            text = ctaText,
                            onClick = { onPlayEpisodeClick(anime, resumeEp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Episode streaming belum tersedia untuk anime ini.",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    // Genre Pills
                    if (anime.genres.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            anime.displayGenres.forEach { genre ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = genre,
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Synopsis Box
                    // Synopsis Box with Translation Support
                    val rawSynopsis = anime.cleanSynopsis ?: anime.synopsis
                    if (!rawSynopsis.isNullOrEmpty()) {
                        Column(modifier = Modifier.animateContentSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Sinopsis",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceVariant)
                                        .clickable {
                                            if (translatedSynopsis != null) {
                                                showOriginalSynopsis = !showOriginalSynopsis
                                            } else if (!isTranslating) {
                                                isTranslating = true
                                                scope.launch {
                                                    ApiClient.translateText(rawSynopsis).onSuccess { tr ->
                                                        translatedSynopsis = tr
                                                        showOriginalSynopsis = false
                                                    }
                                                    isTranslating = false
                                                }
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    if (isTranslating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = CrunchyOrange
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Translate,
                                            contentDescription = null,
                                            tint = CrunchyOrange,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = when {
                                            isTranslating -> "Menerjemahkan..."
                                            translatedSynopsis != null && !showOriginalSynopsis -> "Teks Asli"
                                            translatedSynopsis != null -> "Terjemahan"
                                            else -> "Terjemahkan"
                                        },
                                        fontSize = 11.sp,
                                        color = CrunchyOrange,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val currentSynopsisText = if (translatedSynopsis != null && !showOriginalSynopsis) {
                                translatedSynopsis!!
                            } else {
                                rawSynopsis
                            }
                            Text(
                                text = currentSynopsisText,
                                fontSize = 13.sp,
                                color = TextMuted,
                                lineHeight = 19.sp,
                                maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isSynopsisExpanded) "Tutup Sinopsis" else "Baca Selengkapnya",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrunchyOrange,
                                modifier = Modifier.clickable { isSynopsisExpanded = !isSynopsisExpanded }
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            // 4. Episode List
            if (totalEpisodes > 0) {
                item {
                    SectionHeader(title = "Daftar Episode ($totalEpisodes)")
                }
            items(totalEpisodes) { index ->
                val epNum = index + 1
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPlayEpisodeClick(anime, epNum) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Episode Thumbnail with Play Overlay
                        Box(
                            modifier = Modifier
                                .size(width = 96.dp, height = 58.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                        ) {
                            AsyncImage(
                                model = anime.banner ?: anime.poster,
                                contentDescription = "Ep $epNum",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = CrunchyOrange,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            if (watchedHistory != null && watchedHistory?.episode == epNum) {
                                LinearProgressIndicator(
                                    progress = { (watchedHistory!!.progress.toFloat() / 100f).coerceIn(0f, 1f) },
                                    color = CrunchyOrange,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Episode $epNum",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val epStatus = if (watchedHistory != null && watchedHistory?.episode == epNum) {
                                "Sedang Ditonton (${watchedHistory!!.progress}%)"
                            } else if (watchedHistory != null && (watchedHistory?.episode ?: 0) > epNum) {
                                "Sudah Ditonton • Takarir Indonesia"
                            } else {
                                "Takarir Indonesia • HD 1080p"
                            }
                            Text(
                                text = epStatus,
                                fontSize = 11.sp,
                                color = if (watchedHistory != null && watchedHistory?.episode == epNum) CrunchyOrange else TextMuted
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Putar",
                            tint = CrunchyOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            } else {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (isNotYetReleased) Icons.Default.CalendarMonth else Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = CrunchyOrange,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isNotYetReleased) "Episode Belum Dirilis" else "Belum Ada Episode",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isNotYetReleased) {
                                    "Anime ini masih dalam tahap produksi/jadwal mendatang. Tunggu tanggal rilis resminya!"
                                } else {
                                    "Episode belum dipublikasikan oleh server penyedia streaming."
                                },
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
