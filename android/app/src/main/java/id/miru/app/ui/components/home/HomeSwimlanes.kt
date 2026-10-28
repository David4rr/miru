package id.miru.app.ui.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.miru.app.data.AnimeCard
import id.miru.app.data.HomeCatalog
import id.miru.app.data.RecommendationEngine.RecommendationResult
import id.miru.app.data.WatchHistoryItem
import id.miru.app.ui.components.AnimePosterCard
import id.miru.app.ui.components.ContinueWatchingCard
import id.miru.app.ui.components.HeroBannerCarousel
import id.miru.app.ui.components.SectionHeader

fun LazyListScope.homeCatalogSwimlanes(
    catalog: HomeCatalog,
    onAnimeClick: (AnimeCard) -> Unit,
    onContinueWatchClick: (WatchHistoryItem) -> Unit,
    onScheduleClick: () -> Unit,
    parallaxOffset: Float = 0f,
    recommendation: RecommendationResult? = null
) {
    if (catalog.hero.isNotEmpty()) {
        item {
            HeroBannerCarousel(
                items = catalog.hero,
                onItemClick = onAnimeClick,
                onWatchClick = onAnimeClick,
                parallaxOffset = parallaxOffset
            )
        }
    }

    if (catalog.continueWatching.isNotEmpty()) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(title = "Lanjutkan Menonton")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(catalog.continueWatching) { item ->
                    ContinueWatchingCard(item = item, onClick = onContinueWatchClick)
                }
            }
        }
    }

    if (recommendation != null && recommendation.items.isNotEmpty()) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(
                title = recommendation.title,
                subtitle = recommendation.subtitle,
                badge = recommendation.badge
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(recommendation.items) { anime ->
                    AnimePosterCard(anime = anime, onClick = onAnimeClick)
                }
            }
        }
    }

    if (catalog.airingToday.isNotEmpty()) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Rilis Hari Ini", onSeeAllClick = onScheduleClick)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(catalog.airingToday) { scheduleItem ->
                    AnimePosterCard(anime = scheduleItem.anime, onClick = onAnimeClick)
                }
            }
        }
    }

    if (catalog.topAiring.isNotEmpty()) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Sedang Tayang Terpopuler")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(catalog.topAiring) { idx, anime ->
                    AnimePosterCard(anime = anime, onClick = onAnimeClick, rank = if (idx < 3) idx + 1 else null)
                }
            }
        }
    }

    if (catalog.seasonal.isNotEmpty()) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Populer Musim Ini")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(catalog.seasonal) { anime ->
                    AnimePosterCard(anime = anime, onClick = onAnimeClick)
                }
            }
        }
    }

    if (catalog.topRated.isNotEmpty()) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Anime Terbaik (Nilai Tertinggi)")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(catalog.topRated) { anime ->
                    AnimePosterCard(anime = anime, onClick = onAnimeClick)
                }
            }
        }
    }

    if (catalog.upcoming.isNotEmpty()) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Akan Datang (Segera Hadir)")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(catalog.upcoming) { anime ->
                    AnimePosterCard(anime = anime, onClick = onAnimeClick)
                }
            }
        }
    }

    if (catalog.allTimePopular.isNotEmpty()) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Terpopuler Sepanjang Masa")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(catalog.allTimePopular) { anime ->
                    AnimePosterCard(anime = anime, onClick = onAnimeClick)
                }
            }
        }
    }
}
