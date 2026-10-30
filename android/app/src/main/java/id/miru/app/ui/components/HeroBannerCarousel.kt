package id.miru.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import id.miru.app.data.AnimeCard
import id.miru.app.ui.components.home.HeroBannerItem
import id.miru.app.ui.theme.CrunchyOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HeroBannerCarousel(
    items: List<AnimeCard>,
    onItemClick: (AnimeCard) -> Unit,
    onWatchClick: (AnimeCard) -> Unit,
    modifier: Modifier = Modifier,
    parallaxOffset: Float = 0f,
    parallaxOffsetProvider: (() -> Float)? = null
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            delay(5000)
            val nextPage = (pagerState.currentPage + 1) % items.size
            pagerState.animateScrollToPage(
                page = nextPage,
                animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Soft ambient bloom behind active card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                CrunchyOrange.copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            )

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                val absOffset = abs(pageOffset).coerceIn(0f, 1f)
                HeroBannerItem(
                    anime = items[page],
                    onItemClick = onItemClick,
                    onWatchClick = onWatchClick,
                    parallaxOffset = parallaxOffset,
                    parallaxOffsetProvider = parallaxOffsetProvider,
                    pageOffset = pageOffset,
                    absOffset = absOffset
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Pager Indicators with generous breathing room and spaced-out dots
        Row(
            modifier = Modifier.padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(items.size) { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateDpAsState(
                    targetValue = if (isSelected) 24.dp else 7.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "dotWidth"
                )
                Box(
                    modifier = Modifier
                        .size(width = width, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) CrunchyOrange else Color.White.copy(alpha = 0.35f))
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                )
            }
        }
    }
}
