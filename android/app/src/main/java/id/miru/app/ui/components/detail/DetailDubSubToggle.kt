package id.miru.app.ui.components.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.AudioMode
import id.miru.app.ui.theme.CrunchyOrange

@Composable
fun DetailDubSubToggle(
    currentMode: AudioMode,
    onModeSelected: (AudioMode) -> Unit,
    hasDub: Boolean = true,
    modifier: Modifier = Modifier
) {
    val modes = if (hasDub) listOf(AudioMode.SUB, AudioMode.DUB) else listOf(AudioMode.SUB)
    val pillShape = RoundedCornerShape(20.dp)

    Row(
        modifier = modifier
            .clip(pillShape)
            .background(Color(0xFF14171F).copy(alpha = 0.85f))
            .border(1.dp, Color(0xFF2E3440), pillShape)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { mode ->
            val isSelected = mode == currentMode
            val bgTarget = if (isSelected) CrunchyOrange else Color.Transparent
            val textTarget = if (isSelected) Color.White else Color(0xFF8C95A6)
            val animatedBg by animateColorAsState(targetValue = bgTarget, label = "audioToggleBg")
            val animatedText by animateColorAsState(targetValue = textTarget, label = "audioToggleText")

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(animatedBg)
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = animatedText
                )
            }
        }
    }
}
