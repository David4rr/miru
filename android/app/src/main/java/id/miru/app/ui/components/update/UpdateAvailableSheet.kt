package id.miru.app.ui.components.update

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.AppRelease
import id.miru.app.data.UpdateDownloadState
import id.miru.app.data.UpdateManager
import id.miru.app.ui.theme.BorderSubtle
import id.miru.app.ui.theme.CrunchyrollOrange
import id.miru.app.ui.theme.SurfaceDark
import id.miru.app.ui.theme.SurfaceVariantDark
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary
import id.miru.app.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableBottomSheet(
    release: AppRelease,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    fun startDownload() {
        val downloadUrl = release.downloadUrl
        if (downloadUrl.isNullOrEmpty()) {
            Toast.makeText(context, "URL unduhan APK tidak ditemukan di rilis ini", Toast.LENGTH_LONG).show()
            return
        }
        downloadState = UpdateDownloadState.Downloading(0f, 0L, release.apkSize)
        downloadJob = scope.launch {
            val result = UpdateManager.downloadApk(context = context, downloadUrl = downloadUrl, targetFileName = "Miru-${release.tagName}.apk") { p, d, t ->
                downloadProgress = p; downloadedBytes = d; totalBytes = t
                downloadState = UpdateDownloadState.Downloading(p, d, t)
            }
            result.fold(
                onSuccess = { apkFile -> downloadState = UpdateDownloadState.ReadyToInstall(apkFile) },
                onFailure = { err -> downloadState = UpdateDownloadState.Error(err.message ?: "Gagal mengunduh") }
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (downloadState is UpdateDownloadState.Downloading) downloadJob?.cancel()
            onDismissRequest()
        },
        containerColor = SurfaceDark,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(CrunchyrollOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, tint = CrunchyrollOrange, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Pembaruan Tersedia", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(CrunchyrollOrange).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(text = release.tagName, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = "Versi saat ini: v${UpdateManager.currentVersion}" + if (release.displaySize.isNotEmpty()) " • Ukuran: ${release.displaySize}" else "",
                        color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Catatan Pembaruan", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))

            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    item { FormattedChangelogContent(body = release.body) }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (val state = downloadState) {
                is UpdateDownloadState.Idle -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onDismissRequest, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                            Text(text = "Nanti Saja", fontSize = 14.sp)
                        }
                        Button(
                            onClick = { startDownload() },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange, contentColor = Color.Black)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Unduh & Perbarui", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is UpdateDownloadState.Downloading -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Mengunduh pembaruan...", color = TextPrimary, fontSize = 13.sp)
                            Text(text = "${(downloadProgress * 100).toInt()}%", color = CrunchyrollOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = CrunchyrollOrange, trackColor = BorderSubtle)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = String.format(java.util.Locale.US, "%.1f MB / %.1f MB", downloadedBytes / 1048576.0, totalBytes / 1048576.0), color = TextMuted, fontSize = 11.sp)
                            TextButton(onClick = { downloadJob?.cancel(); downloadState = UpdateDownloadState.Idle }) { Text(text = "Batalkan", color = TextMuted, fontSize = 12.sp) }
                        }
                    }
                }
                is UpdateDownloadState.ReadyToInstall -> {
                    Button(
                        onClick = { UpdateManager.installApk(context, state.apkFile) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Pasang Pembaruan Sekarang", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateDownloadState.Error -> {
                    Text(text = "Gagal Mengunduh: ${state.message}", color = Color(0xFFFF5252), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { startDownload() }, colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange, contentColor = Color.Black)) { Text("Coba Lagi") }
                }
            }
        }
    }
}
