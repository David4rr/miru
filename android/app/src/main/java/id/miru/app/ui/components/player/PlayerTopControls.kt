package id.miru.app.ui.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.ui.theme.CrunchyOrange

val PlayerTopScrimBrush = Brush.verticalGradient(
    colors = listOf(
        Color.Black.copy(alpha = 0.75f),
        Color.Black.copy(alpha = 0.35f),
        Color.Transparent
    )
)

@Composable
fun PlayerTopControls(
    title: String,
    episode: Int,
    isScreenLocked: Boolean,
    isSubtitleActive: Boolean = true,
    aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    isCustomQuality: Boolean = false,
    onOpenSubtitleSheet: () -> Unit,
    onOpenQualitySheet: () -> Unit,
    onOpenSourceSheet: () -> Unit = {},
    onToggleAspectRatio: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleLock: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Episode $episode",
                color = CrunchyOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerTopActionButton(
                onClick = onOpenSourceSheet,
                icon = Icons.Default.Dns,
                contentDescription = "Sumber & Server"
            )

            PlayerTopActionButton(
                onClick = onToggleAspectRatio,
                icon = Icons.Default.AspectRatio,
                contentDescription = "Rasio Layar: ${aspectRatioMode.label}",
                isActive = aspectRatioMode != AspectRatioMode.FIT
            )

            PlayerTopActionButton(
                onClick = onOpenQualitySheet,
                icon = Icons.Default.HighQuality,
                contentDescription = "Kualitas Video",
                isActive = isCustomQuality
            )

            PlayerTopActionButton(
                onClick = onOpenSubtitleSheet,
                icon = if (isSubtitleActive) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                contentDescription = "Pilih Subtitle",
                isActive = isSubtitleActive,
                inactiveTint = Color.White.copy(alpha = 0.45f)
            )

            PlayerTopActionButton(
                onClick = onEnterPip,
                icon = Icons.Default.PictureInPictureAlt,
                contentDescription = "Mode PiP"
            )

            PlayerTopActionButton(
                onClick = onToggleLock,
                icon = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Kunci Layar",
                isActive = isScreenLocked
            )
        }
    }
}

@Composable
private fun PlayerTopActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeTint: Color = CrunchyOrange,
    inactiveTint: Color = Color.White,
    iconSize: Dp = 20.dp
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) activeTint else inactiveTint,
            modifier = Modifier.size(iconSize)
        )
    }
}
