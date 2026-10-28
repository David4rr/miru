package id.miru.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import id.miru.app.BuildConfig
import id.miru.app.ui.components.report.ISSUE_CATEGORIES
import id.miru.app.ui.components.report.ReportIssueForm
import id.miru.app.ui.theme.CrunchyrollOrange
import id.miru.app.ui.theme.DarkOled
import id.miru.app.ui.theme.DarkSurface
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueBottomSheet(
    animeTitle: String? = null,
    episodeNumber: Int? = null,
    errorMessage: String? = null,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedCategory by remember { mutableStateOf(ISSUE_CATEGORIES[0]) }
    var userDescription by remember { mutableStateOf("") }
    var isCopied by remember { mutableStateOf(false) }

    fun buildDiagnosticReport(): String {
        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        return buildString {
            appendLine("### Laporan Masalah Miru Anime")
            appendLine("- **Aplikasi:** Miru v${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})")
            appendLine("- **Perangkat:** $deviceName")
            appendLine("- **Android:** ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("- **Kategori:** $selectedCategory")
            if (!animeTitle.isNullOrBlank()) appendLine("- **Anime:** $animeTitle")
            if (episodeNumber != null) appendLine("- **Episode:** $episodeNumber")
            if (!errorMessage.isNullOrBlank()) appendLine("- **Pesan Error:** $errorMessage")
            appendLine()
            appendLine("#### Deskripsi Pengguna:")
            appendLine(userDescription.ifBlank { "Tidak ada deskripsi tambahan." })
        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(CrunchyrollOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.BugReport, contentDescription = null, tint = CrunchyrollOrange, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "Laporkan Masalah / Bug", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "Bantu kami memperbaiki kendala pada aplikasi", fontSize = 12.sp, color = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(text = "DATA DIAGNOSTIK OTOMATIS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CrunchyrollOrange)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Miru v${BuildConfig.VERSION_NAME} • ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})", fontSize = 12.sp, color = TextPrimary)
                if (!animeTitle.isNullOrBlank()) Text(text = "Target: $animeTitle (Episode ${episodeNumber ?: "-"})", fontSize = 11.sp, color = TextMuted)
                if (!errorMessage.isNullOrBlank()) Text(text = "Error: $errorMessage", fontSize = 11.sp, color = Color(0xFFFF5252), maxLines = 2)
            }

            Spacer(modifier = Modifier.height(16.dp))

            ReportIssueForm(
                selectedCategory = selectedCategory,
                onCategorySelect = { selectedCategory = it },
                userDescription = userDescription,
                onDescriptionChange = { userDescription = it },
                isCopied = isCopied,
                onCopyClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Laporan Bug Miru", buildDiagnosticReport()))
                    isCopied = true
                    Toast.makeText(context, "Laporan disalin ke papan klip!", Toast.LENGTH_SHORT).show()
                },
                onSubmitClick = {
                    val report = buildDiagnosticReport()
                    val title = if (!animeTitle.isNullOrBlank()) "[$selectedCategory] $animeTitle (Ep ${episodeNumber ?: "?"})" else "[$selectedCategory] ${userDescription.take(40).ifBlank { "Kendala Aplikasi" }}"
                    try {
                        val encTitle = URLEncoder.encode(title, "UTF-8")
                        val encBody = URLEncoder.encode(report, "UTF-8")
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/David4rr/miru/issues/new?title=$encTitle&body=$encBody")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                        onDismissRequest()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Tidak dapat membuka browser", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
