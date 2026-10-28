package id.miru.app.ui.components.detail

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.AnimeCard
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkBackground

@Composable
fun DetailTopBar(
    anime: AnimeCard,
    scrollOffset: Float,
    resumeEpisode: Int,
    isBookmarked: Boolean,
    onBackClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onPlayEpisodeClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val morphProgress by remember(scrollOffset) {
        derivedStateOf { ((scrollOffset - 60f) / 140f).coerceIn(0f, 1f) }
    }
    val backSrc = remember { MutableInteractionSource() }
    val isBackPressed by backSrc.collectIsPressedAsState()
    val backScale by animateFloatAsState(
        targetValue = if (isBackPressed) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "backPress"
    )
    val playSrc = remember { MutableInteractionSource() }
    val isPlayPressed by playSrc.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "playPress"
    )

    val bgModifier = if (morphProgress > 0f) {
        Modifier.background(DarkBackground.copy(alpha = morphProgress))
    } else {
        Modifier.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(bgModifier)
            .statusBarsPadding()
    ) {
        if (morphProgress >= 0.95f) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomCenter).background(Color(0xFF26262B)))
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = backScale; scaleY = backScale }
                    .size(40.dp).clip(CircleShape)
                    .background(if (morphProgress > 0.5f) Color(0xFF1B1C22) else Color.Black.copy(alpha = 0.5f))
                    .border(1.dp, if (morphProgress > 0.5f) Color(0xFF2A2B32) else Color.White.copy(alpha = 0.15f), CircleShape)
                    .clickable(interactionSource = backSrc, indication = null, onClick = onBackClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp).graphicsLayer {
                    alpha = morphProgress
                    translationY = (1f - morphProgress) * 14f
                }
            ) {
                Text(anime.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!anime.isNotYetReleased) {
                    Text("Episode $resumeEpisode", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CrunchyOrange)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = morphProgress > 0.75f && !anime.isNotYetReleased,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) + scaleIn(spring(stiffness = Spring.StiffnessMedium)),
                    exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(spring(stiffness = Spring.StiffnessMedium))
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .graphicsLayer { scaleX = playScale; scaleY = playScale }
                            .clip(RoundedCornerShape(8.dp)).background(CrunchyOrange)
                            .clickable(interactionSource = playSrc, indication = null) { onPlayEpisodeClick(resumeEpisode) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Putar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Simpan",
                        tint = if (isBookmarked) CrunchyOrange else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
