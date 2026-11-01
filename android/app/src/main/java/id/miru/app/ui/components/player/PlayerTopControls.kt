package id.miru.app.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.ui.theme.CrunchyOrange

@Composable
fun PlayerTopControls(
    title: String,
    episode: Int,
    isScreenLocked: Boolean,
    isSubtitleActive: Boolean = true,
    aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
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

        IconButton(
            onClick = onOpenSourceSheet,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.Dns, contentDescription = "Sumber & Server", tint = Color.White, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = onToggleAspectRatio,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.AspectRatio, contentDescription = "Rasio Layar: ${aspectRatioMode.label}", tint = Color.White, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = onOpenQualitySheet,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.HighQuality, contentDescription = "Kualitas Video", tint = Color.White, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = onOpenSubtitleSheet,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = if (isSubtitleActive) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                contentDescription = "Pilih Subtitle",
                tint = if (isSubtitleActive) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = onEnterPip,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Mode PiP", tint = Color.White, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = onToggleLock,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(if (isScreenLocked) CrunchyOrange.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.4f))
        ) {
            Icon(
                imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Kunci Layar",
                tint = if (isScreenLocked) CrunchyOrange else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
