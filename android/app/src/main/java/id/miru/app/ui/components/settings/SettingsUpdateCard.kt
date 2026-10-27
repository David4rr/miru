package id.miru.app.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.AppRelease
import id.miru.app.data.UpdateManager
import id.miru.app.ui.theme.CrunchyrollOrange
import id.miru.app.ui.theme.SurfaceDark
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary

@Composable
fun SettingsUpdateCard(
    isCheckingUpdate: Boolean,
    updateCheckResult: AppRelease?,
    hasChecked: Boolean,
    onCheckUpdateClick: () -> Unit,
    onChangelogClick: () -> Unit,
    onUpdateBannerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(CrunchyrollOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = CrunchyrollOrange, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Pembaruan Aplikasi", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(CrunchyrollOrange.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(text = "v${UpdateManager.currentVersion}", color = CrunchyrollOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    val statusText = when {
                        isCheckingUpdate -> "Sedang memeriksa rilis terbaru..."
                        updateCheckResult != null && updateCheckResult.isNewer -> "Versi ${updateCheckResult.tagName} tersedia untuk diunduh"
                        hasChecked -> "Versi terpasang sudah yang paling baru"
                        else -> "Ketuk untuk memeriksa rilis APK terbaru"
                    }
                    Text(text = statusText, color = if (updateCheckResult?.isNewer == true) CrunchyrollOrange else TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCheckUpdateClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                    enabled = !isCheckingUpdate, colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange, contentColor = Color.Black)
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isCheckingUpdate) "Memeriksa..." else "Cek Update", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onChangelogClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Icon(imageVector = Icons.Default.HistoryEdu, contentDescription = null, modifier = Modifier.size(16.dp), tint = CrunchyrollOrange)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Changelog", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (updateCheckResult != null && updateCheckResult.isNewer) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onUpdateBannerClick() },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CrunchyrollOrange.copy(alpha = 0.15f))
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.NewReleases, contentDescription = null, tint = CrunchyrollOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Rilis ${updateCheckResult.tagName} Siap Diunduh", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Ketuk untuk melihat detail dan pasang", color = CrunchyrollOrange, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
