package id.miru.app.ui.screens

import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.miru.app.data.ApiClient
import id.miru.app.data.AnimeCard
import id.miru.app.data.ScheduleItem
import id.miru.app.data.WeeklySchedule
import id.miru.app.ui.components.OrangeButton
import id.miru.app.ui.theme.CrunchyrollOrange
import id.miru.app.ui.theme.DarkOled
import id.miru.app.ui.theme.DarkSurface
import id.miru.app.ui.theme.DarkSurfaceVariant
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary

@Composable
fun ScheduleScreen(
    onAnimeClick: (AnimeCard) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var schedule by remember { mutableStateOf<WeeklySchedule?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedDayIndex by remember { mutableIntStateOf(0) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun loadSchedule() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = ApiClient.getWeeklySchedule()
            result.onSuccess { data ->
                schedule = data
                val todayIdx = data.days.indexOfFirst { it.isToday }
                if (todayIdx >= 0) {
                    selectedDayIndex = todayIdx
                }
                isLoading = false
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "Gagal memuat jadwal rilis"
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadSchedule()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkOled)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "Jadwal Rilis Anime",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Penayangan simulcast mingguan",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        when {
            isLoading && schedule == null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = CrunchyrollOrange, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Memuat jadwal penayangan...",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }

            errorMessage != null && schedule == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Koneksi Bermasalah",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Gagal memuat jadwal rilis",
                        fontSize = 13.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OrangeButton(
                        text = "Coba Lagi",
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            loadSchedule()
                        }
                    )
                }
            }

            schedule != null -> {
                val currentDays = schedule!!.days

                // Day Selection Tabs
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(currentDays) { index, day ->
                        val isSelected = index == selectedDayIndex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CrunchyrollOrange else DarkSurface)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) CrunchyrollOrange else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedDayIndex = index }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = day.dayName,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (day.isToday) "Hari Ini" else day.dateFormatted,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else TextMuted
                                )
                            }
                        }
                    }
                }

                val activeDay = currentDays.getOrNull(selectedDayIndex)
                val items = activeDay?.items.orEmpty()

                if (items.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tidak Ada Jadwal Tayang",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Belum ada anime simulcast yang dijadwalkan pada hari ${activeDay?.dayName ?: ""}.",
                            fontSize = 13.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items, key = { it.id }) { scheduleItem ->
                            ScheduleRowCard(
                                item = scheduleItem,
                                onClick = { onAnimeClick(scheduleItem.anime) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleRowCard(
    item: ScheduleItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val anime = item.anime
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 90.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = anime.poster,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            // Badges row: Air Time & Episode
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CrunchyrollOrange.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = CrunchyrollOrange,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.formattedTime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrunchyrollOrange
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Episode ${item.episode}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Title
            Text(
                text = anime.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Genres & Score
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (anime.score != null && anime.score > 0.0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = anime.displayScore,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                if (anime.genres.isNotEmpty()) {
                    Text(
                        text = anime.genres.take(2).joinToString(" • "),
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
