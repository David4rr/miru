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
    parallaxOffsetProvider: (() -> Float)? = null,
    recommendation: RecommendationResult? = null
) {
    if (catalog.hero.isNotEmpty()) {
        item(key = "home_hero_banner", contentType = "hero_banner") {
            HeroBannerCarousel(
                items = catalog.hero,
                onItemClick = onAnimeClick,
                onWatchClick = onAnimeClick,
                parallaxOffset = parallaxOffset,
                parallaxOffsetProvider = parallaxOffsetProvider
            )
        }
    }

    if (catalog.continueWatching.isNotEmpty()) {
        item(key = "home_continue_watching", contentType = "row_continue") {
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(title = "Lanjutkan Menonton")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(catalog.continueWatching, key = { "cw_${it.slug}_${it.episode}" }, contentType = { "cw_card" }) { item ->
                    ContinueWatchingCard(item = item, onClick = onContinueWatchClick)
                }
            }
        }
    }

    if (recommendation != null && recommendation.items.isNotEmpty()) {
        item(key = "home_recommendations", contentType = "row_anime") {
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
                items(recommendation.items, key = { "rec_${it.id}_${it.slug}" }, contentType = { "anime_card" }) { anime ->
                    AnimePosterCard(anime = anime, onClick = onAnimeClick)
                }
            }
        }
    }

    if (catalog.airingToday.isNotEmpty()) {
        item(key = "home_airing_today", contentType = "row_anime") {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Rilis Hari Ini", onSeeAllClick = onScheduleClick)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(catalog.airingToday, key = { "airing_${it.anime.id}_${it.anime.slug}" }, contentType = { "anime_card" }) { scheduleItem ->
                    val badge = if (scheduleItem.isAired) "Ep ${scheduleItem.episode} • Rilis" else scheduleItem.formattedTime
                    AnimePosterCard(anime = scheduleItem.anime, onClick = onAnimeClick, customBadge = badge)
                }
            }
        }
    }

    if (catalog.topAiring.isNotEmpty()) {
        item(key = "home_top_airing", contentType = "row_anime") {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Sedang Tayang Terpopuler")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(catalog.topAiring, key = { _, anime -> "top_${anime.id}_${anime.slug}" }, contentType = { _, _ -> "anime_card" }) { idx, anime ->
                    AnimePosterCard(anime = anime, onClick = onAnimeClick, rank = if (idx < 3) idx + 1 else null)
                }
            }
        }
    }

    if (catalog.seasonal.isNotEmpty()) {
        item(key = "home_seasonal", contentType = "row_anime") {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Populer Musim Ini")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(catalog.seasonal, key = { "seasonal_${it.id}_${it.slug}" }, contentType = { "anime_card" }) { anime ->
                    AnimePosterCard(anime = anime, onClick = onAnimeClick)
                }
            }
        }
    }

    if (catalog.topRated.isNotEmpty()) {
        item(key = "home_top_rated", contentType = "row_anime") {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Anime Terbaik (Nilai Tertinggi)")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(catalog.topRated, key = { "rated_${it.id}_${it.slug}" }, contentType = { "anime_card" }) { anime ->
                    AnimePosterCard(anime = anime, onClick = onAnimeClick)
                }
            }
        }
    }

    if (catalog.upcoming.isNotEmpty()) {
        item(key = "home_upcoming", contentType = "row_anime") {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Akan Datang (Segera Hadir)")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(catalog.upcoming, key = { "upcoming_${it.id}_${it.slug}" }, contentType = { "anime_card" }) { anime ->
                    AnimePosterCard(anime = anime, onClick = onAnimeClick)
                }
            }
        }
    }

    if (catalog.allTimePopular.isNotEmpty()) {
        item(key = "home_all_time_popular", contentType = "row_anime") {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Terpopuler Sepanjang Masa")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(catalog.allTimePopular, key = { "pop_${it.id}_${it.slug}" }, contentType = { "anime_card" }) { anime ->
                    AnimePosterCard(anime = anime, onClick = onAnimeClick)
                }
            }
        }
    }
}
