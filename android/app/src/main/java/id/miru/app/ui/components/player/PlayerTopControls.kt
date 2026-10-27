package id.miru.app.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
    isAutoSkipEnabled: Boolean,
    onToggleAutoSkip: () -> Unit,
    isScreenLocked: Boolean,
    onToggleLock: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = "Episode $episode", color = CrunchyOrange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Auto-Skip Toggle
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isAutoSkipEnabled) CrunchyOrange.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f))
                .border(1.dp, if (isAutoSkipEnabled) CrunchyOrange else Color(0xFF333540), RoundedCornerShape(16.dp))
                .clickable { onToggleAutoSkip() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.FastForward, contentDescription = null, tint = if (isAutoSkipEnabled) CrunchyOrange else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = if (isAutoSkipEnabled) "Auto Skip" else "Manual", color = if (isAutoSkipEnabled) CrunchyOrange else Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Screen Lock Button
        IconButton(
            onClick = onToggleLock,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isScreenLocked) CrunchyOrange.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.4f))
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
