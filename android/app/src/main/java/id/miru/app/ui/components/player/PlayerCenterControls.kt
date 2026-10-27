package id.miru.app.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.miru.app.ui.theme.CrunchyOrange

@Composable
fun PlayerCenterControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    isLoadingStream: Boolean,
    hasNextEpisode: Boolean,
    onPlayPauseClick: () -> Unit,
    onRewind10Click: () -> Unit,
    onForward10Click: () -> Unit,
    onNextEpisodeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
