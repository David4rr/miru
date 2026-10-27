package id.miru.app.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import id.miru.app.ui.theme.DarkOled
import id.miru.app.ui.theme.SurfaceDark
import id.miru.app.ui.theme.SurfaceVariantDark
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary
import id.miru.app.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.OptIn

/**
 * Non-intrusive Update Banner shown at top of screens when an update is available.
 */
@Composable
fun UpdateBanner(
    release: AppRelease,
    onViewUpdateClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CrunchyrollOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = null,
                    tint = CrunchyrollOrange,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Pembaruan Tersedia",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CrunchyrollOrange)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = release.tagName,
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "Versi ${release.tagName} telah dirilis. Ketuk untuk memperbarui.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onViewUpdateClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrunchyrollOrange,
                    contentColor = Color.Black
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Lihat",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onDismissClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tutup",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Interactive Bottom Sheet presenting update information, download progress, and direct APK installation.
 */
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
            val result = UpdateManager.downloadApk(
                context = context,
                downloadUrl = downloadUrl,
                targetFileName = "Tatap-${release.tagName}.apk"
            ) { progress, downloaded, total ->
                downloadProgress = progress
                downloadedBytes = downloaded
                totalBytes = total
                downloadState = UpdateDownloadState.Downloading(progress, downloaded, total)
            }

            result.fold(
                onSuccess = { apkFile ->
                    downloadState = UpdateDownloadState.ReadyToInstall(apkFile)
                },
                onFailure = { err ->
                    downloadState = UpdateDownloadState.Error(err.message ?: "Gagal mengunduh pembaruan")
                }
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (downloadState is UpdateDownloadState.Downloading) {
                downloadJob?.cancel()
            }
            onDismissRequest()
        },
        containerColor = SurfaceDark,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CrunchyrollOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = CrunchyrollOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Pembaruan Tersedia",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CrunchyrollOrange)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = release.tagName,
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Versi saat ini: v${UpdateManager.currentVersion}" +
                                if (release.displaySize.isNotEmpty()) " • Ukuran: ${release.displaySize}" else "",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Release Notes Card
            Text(
                text = "Catatan Pembaruan",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    item {
                        FormattedChangelogContent(body = release.body)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // State & Actions
            when (val state = downloadState) {
                is UpdateDownloadState.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                        ) {
                            Text(text = "Nanti Saja", fontSize = 14.sp)
                        }

                        Button(
                            onClick = { startDownload() },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrunchyrollOrange,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unduh & Perbarui",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is UpdateDownloadState.Downloading -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Mengunduh pembaruan...",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            val percent = (downloadProgress * 100).toInt()
                            Text(
                                text = "$percent%",
                                color = CrunchyrollOrange,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = CrunchyrollOrange,
                            trackColor = BorderSubtle
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val downloadedMb = String.format(java.util.Locale.US, "%.1f", downloadedBytes / (1024.0 * 1024.0))
                        val totalMb = String.format(java.util.Locale.US, "%.1f", totalBytes / (1024.0 * 1024.0))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$downloadedMb MB / $totalMb MB",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            TextButton(
                                onClick = {
                                    downloadJob?.cancel()
                                    downloadState = UpdateDownloadState.Idle
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = "Batalkan",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                is UpdateDownloadState.ReadyToInstall -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!UpdateManager.canInstallPackages(context)) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = CrunchyrollOrange.copy(alpha = 0.15f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Izin Pemasangan Diperlukan",
                                        color = CrunchyrollOrange,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Aktifkan opsi 'Izinkan dari sumber ini' di Pengaturan agar Tatap dapat memperbarui dirinya sendiri.",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { UpdateManager.openInstallPermissionSettings(context) },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CrunchyrollOrange,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Text(text = "Buka Pengaturan Izin", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                UpdateManager.installApk(context, state.apkFile)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrunchyrollOrange,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pasang Pembaruan Sekarang",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is UpdateDownloadState.Error -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Gagal Mengunduh: ${state.message}",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                            ) {
                                Text(text = "Tutup", fontSize = 14.sp)
                            }
                            Button(
                                onClick = { startDownload() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CrunchyrollOrange,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text(text = "Coba Lagi", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen style ModalBottomSheet displaying historical Changelogs for all Tatap versions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var changelogList by remember { mutableStateOf<List<AppRelease>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        scope.launch {
            val result = UpdateManager.fetchChangelogHistory()
            changelogList = result.getOrElse { UpdateManager.getEmbeddedChangelogs() }
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = DarkOled,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CrunchyrollOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HistoryEdu,
                        contentDescription = null,
                        tint = CrunchyrollOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Catatan Rilis (Changelog)",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Riwayat pembaruan dan pengembangan Tatap",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextSecondary
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = CrunchyrollOrange,
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(changelogList) { item ->
                        val isCurrentInstalled = item.versionName == UpdateManager.currentVersion ||
                                item.tagName == "v${UpdateManager.currentVersion}"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.tagName,
                                            color = TextPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (isCurrentInstalled) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(CrunchyrollOrange)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Terpasang",
                                                    color = Color.Black,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    if (item.publishedAt.isNotEmpty()) {
                                        Text(
                                            text = item.publishedAt.take(10),
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                FormattedChangelogContent(body = item.body)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clean structured changelog renderer that transforms raw markdown headers and bullet points
 * into attractive OLED-styled section headers, tag pills, and annotated list items.
 */
@Composable
fun FormattedChangelogContent(
    body: String,
    modifier: Modifier = Modifier
) {
    val lines = remember(body) { body.lines() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> {
                    Spacer(modifier = Modifier.height(2.dp))
                }
                line.startsWith("### ") -> {
                    val headerText = line.removePrefix("### ").trim()
                    Text(
                        text = headerText,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                line.startsWith("#### ") -> {
                    val sectionText = line.removePrefix("#### ").trim().trimEnd(':')
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(CrunchyrollOrange)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sectionText,
                            color = CrunchyrollOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val bulletText = line.substring(2).trim()
                    val regex = Regex("""^\*\*(.+?)\*\*:?\s*(.*)$""")
                    val match = regex.find(bulletText)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 2.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(CrunchyrollOrange.copy(alpha = 0.8f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (match != null) {
                            val title = match.groupValues[1]
                            val desc = match.groupValues[2]
                            Text(
                                text = androidx.compose.ui.text.buildAnnotatedString {
                                    androidx.compose.ui.text.withStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    ) {
                                        append(title)
                                        if (desc.isNotEmpty()) append(": ")
                                    }
                                    androidx.compose.ui.text.withStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            color = TextSecondary
                                        )
                                    ) {
                                        append(desc)
                                    }
                                },
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        } else {
                            val cleanText = bulletText.replace("**", "")
                            Text(
                                text = cleanText,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
                else -> {
                    val cleanText = line.replace("**", "")
                    Text(
                        text = cleanText,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}
