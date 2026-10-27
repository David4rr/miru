package id.miru.app.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.ui.theme.CrunchyrollOrange
import id.miru.app.ui.theme.SurfaceDark
import id.miru.app.ui.theme.SurfaceVariantDark
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary

@Composable
fun SettingsEngineCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(text = "STATUS SISTEM CORE", color = CrunchyrollOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(12.dp))
            EngineInfoRow(icon = Icons.Default.Dns, title = "Rust Axum Daemon", subtitle = "http://127.0.0.1:8767", statusBadge = "Aktif")
            Spacer(modifier = Modifier.height(10.dp))
            EngineInfoRow(icon = Icons.Default.Speed, title = "Stream Engine", subtitle = "ZokoAnime XOR v1 + HiAnime Fallback", statusBadge = "Siap")
            Spacer(modifier = Modifier.height(10.dp))
            EngineInfoRow(icon = Icons.Default.CheckCircle, title = "Subtitle & Translation", subtitle = "Google GTX+ & AniSkip v2", statusBadge = "Tersambung")
        }
    }
}

@Composable
private fun EngineInfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    statusBadge: String
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(SurfaceVariantDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = CrunchyrollOrange, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF2E7D32).copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(text = statusBadge, color = Color(0xFF81C784), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
