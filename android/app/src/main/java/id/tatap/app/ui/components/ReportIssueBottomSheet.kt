package id.tatap.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tatap.app.BuildConfig
import id.tatap.app.ui.theme.CrunchyrollOrange
import id.tatap.app.ui.theme.DarkOled
import id.tatap.app.ui.theme.DarkSurface
import id.tatap.app.ui.theme.DarkSurfaceVariant
import id.tatap.app.ui.theme.TextMuted
import id.tatap.app.ui.theme.TextPrimary
import java.net.URLEncoder

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    initialCategory: String = "Umum",
    animeTitle: String? = null,
    episodeNumber: Int? = null,
    errorMessage: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var userDescription by remember { mutableStateOf("") }
    var isCopied by remember { mutableStateOf(false) }

    val categories = listOf(
        "Video Rusak / Buffer",
        "Subtitle Bermasalah",
        "Tampilan / UI",
        "Fitur Tidak Berfungsi",
        "Umum"
    )

    fun buildDiagnosticReport(): String {
        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val appVersion = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        return buildString {
            appendLine("### Laporan Masalah Tatap Anime")
            appendLine("- **Kategori:** $selectedCategory")
            if (!animeTitle.isNullOrBlank()) {
                appendLine("- **Anime:** $animeTitle")
            }
            if (episodeNumber != null && episodeNumber > 0) {
                appendLine("- **Episode:** $episodeNumber")
            }
            if (!errorMessage.isNullOrBlank()) {
                appendLine("- **Pesan Error:** $errorMessage")
            }
            appendLine()
            appendLine("### Diagnostik Perangkat")
            appendLine("- **Versi Aplikasi:** $appVersion")
            appendLine("- **Perangkat:** $deviceName")
            appendLine("- **Sistem Operasi:** $osVersion")
            appendLine("- **Core Engine:** http://127.0.0.1:8767")
            appendLine()
            appendLine("### Catatan Pengguna")
            if (userDescription.isNotBlank()) {
                appendLine(userDescription.trim())
            } else {
                appendLine("(Pengguna tidak menyertakan catatan tambahan)")
            }
        }
    }

    fun openGitHubIssue() {
        val report = buildDiagnosticReport()
        val issueTitle = if (!animeTitle.isNullOrBlank()) {
            "[$selectedCategory] $animeTitle (Ep ${episodeNumber ?: "?"})"
        } else {
            "[$selectedCategory] ${userDescription.take(40).ifBlank { "Kendala Aplikasi" }}"
        }

        try {
            val encodedTitle = URLEncoder.encode(issueTitle, "UTF-8")
            val encodedBody = URLEncoder.encode(report, "UTF-8")
            val url = "https://github.com/David4rr/tatap/issues/new?title=$encodedTitle&body=$encodedBody"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            onDismissRequest()
        } catch (e: Exception) {
            Toast.makeText(context, "Tidak dapat membuka peramban: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard() {
        val report = buildDiagnosticReport()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Laporan Bug Tatap", report)
        clipboard.setPrimaryClip(clip)
        isCopied = true
        Toast.makeText(context, "Laporan berhasil disalin ke papan klip!", Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = DarkOled,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CrunchyrollOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = CrunchyrollOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Laporkan Masalah / Bug",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Bantu kami memperbaiki kendala pada aplikasi",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Diagnostic Summary Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "DATA DIAGNOSTIK OTOMATIS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrunchyrollOrange,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tatap v${BuildConfig.VERSION_NAME} • ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})",
                    fontSize = 12.sp,
                    color = TextPrimary
                )
                if (!animeTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Target: $animeTitle (Episode ${episodeNumber ?: "-"})",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Error: $errorMessage",
                        fontSize = 11.sp,
                        color = Color(0xFFFF5252),
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Selector
            Text(
                text = "Pilih Jenis Kendala",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CrunchyrollOrange else DarkSurfaceVariant)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) CrunchyrollOrange else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Description Input
            Text(
                text = "Keterangan Tambahan (Opsional)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = userDescription,
                onValueChange = { userDescription = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                placeholder = {
                    Text(
                        text = "Contoh: Video tiba-tiba buffering di menit 04:30 atau subtitle tidak muncul...",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = CrunchyrollOrange,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { copyToClipboard() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.2f))
                    )
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isCopied) Color(0xFF4CAF50) else TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCopied) "Tersalin!" else "Salin Laporan",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = { openGitHubIssue() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrunchyrollOrange,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kirim ke GitHub",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
