package id.miru.app.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.TextMuted

@Composable
fun PlayerCenterControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    isLoadingStream: Boolean,
    hasNextEpisode: Boolean,
    errorMessage: String? = null,
    onRetryClick: (() -> Unit)? = null,
    onPlayPauseClick: () -> Unit,
    onRewind10Click: () -> Unit,
    onForward10Click: () -> Unit,
    onNextEpisodeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (errorMessage != null) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xE6141519))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CrunchyOrange, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Gagal Memutar Video", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = errorMessage, color = TextMuted, fontSize = 12.sp, maxLines = 2)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onRetryClick?.invoke() },
                colors = ButtonDefaults.buttonColors(containerColor = CrunchyOrange),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text(text = "Coba Lagi", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onRewind10Click,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(imageVector = Icons.Default.Replay10, contentDescription = "-10 Detik", tint = Color.White, modifier = Modifier.size(28.dp))
        }

        Box(
            modifier = Modifier.size(68.dp).clip(CircleShape).background(CrunchyOrange),
            contentAlignment = Alignment.Center
        ) {
            if (isBuffering || isLoadingStream) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
            } else {
                IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(68.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Jeda" else "Putar",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = onForward10Click,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(imageVector = Icons.Default.Forward10, contentDescription = "+10 Detik", tint = Color.White, modifier = Modifier.size(28.dp))
        }

        if (hasNextEpisode) {
            IconButton(
                onClick = onNextEpisodeClick,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Episode Berikutnya", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}
