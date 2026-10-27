package id.miru.app.ui.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.UpdateManager
import id.miru.app.ui.theme.CrunchyrollOrange
import id.miru.app.ui.theme.SurfaceDark
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary

@Composable
fun SettingsAboutCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(text = "TENTANG APLIKASI", color = CrunchyrollOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Miru for Android", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Versi ${UpdateManager.currentVersion} • Native Kotlin + Rust Core", color = TextMuted, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Aplikasi streaming anime native tanpa WebView dengan akselerasi daemon Rust lokal, pemutar Media3 ExoPlayer, dan desain OLED modern.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}
