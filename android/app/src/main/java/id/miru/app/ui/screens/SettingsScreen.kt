package id.miru.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.ApiClient
import id.miru.app.data.AppRelease
import id.miru.app.data.UpdateManager
import id.miru.app.ui.components.ChangelogModalBottomSheet
import id.miru.app.ui.components.ReportIssueBottomSheet
import id.miru.app.ui.components.UpdateAvailableBottomSheet
import id.miru.app.ui.components.settings.SettingsAboutCard
import id.miru.app.ui.components.settings.SettingsEngineCard
import id.miru.app.ui.components.settings.SettingsStorageCard
import id.miru.app.ui.components.settings.SettingsUpdateCard
import id.miru.app.ui.theme.CrunchyrollOrange
import id.miru.app.ui.theme.DarkOled
import id.miru.app.ui.theme.SurfaceDark
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary
import id.miru.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<AppRelease?>(null) }
    var hasChecked by remember { mutableStateOf(false) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }
    var showChangelogSheet by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    fun checkUpdateNow() {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        scope.launch {
            val result = UpdateManager.checkLatestUpdate()
            isCheckingUpdate = false
            hasChecked = true
            result.fold(
                onSuccess = { release ->
                    updateCheckResult = release
                    if (release.isNewer) showUpdateSheet = true
                    else Toast.makeText(context, "Aplikasi sudah dalam versi terbaru (v${release.versionName})", Toast.LENGTH_SHORT).show()
                },
                onFailure = { err ->
                    Toast.makeText(context, "Gagal memeriksa pembaruan: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(DarkOled).statusBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                Text(text = "PENGATURAN", color = CrunchyrollOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(text = "Preferensi & Pembaruan", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            SettingsUpdateCard(
                isCheckingUpdate = isCheckingUpdate, updateCheckResult = updateCheckResult, hasChecked = hasChecked,
                onCheckUpdateClick = { checkUpdateNow() }, onChangelogClick = { showChangelogSheet = true },
                onUpdateBannerClick = { showUpdateSheet = true }
            )
        }

        item { SettingsEngineCard() }

        item {
            SettingsStorageCard(
                onClearHistoryClick = { showClearHistoryDialog = true },
                onReportIssueClick = { showReportSheet = true }
            )
        }

        item { SettingsAboutCard() }

        item { androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(text = "Bersihkan Semua Riwayat?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(text = "Semua riwayat tontonan dan rekaman progres episode akan dihapus secara permanen.", color = TextSecondary, fontSize = 13.sp) },
            containerColor = SurfaceDark,
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            ApiClient.clearHistory()
                            showClearHistoryDialog = false
                            Toast.makeText(context, "Riwayat tontonan berhasil dibersihkan", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) { Text(text = "Hapus Semua") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text(text = "Batal", color = TextMuted) }
            }
        )
    }

    if (showUpdateSheet && updateCheckResult != null) {
        UpdateAvailableBottomSheet(release = updateCheckResult!!, onDismissRequest = { showUpdateSheet = false })
    }

    if (showChangelogSheet) {
        ChangelogModalBottomSheet(onDismissRequest = { showChangelogSheet = false })
    }

    if (showReportSheet) {
        ReportIssueBottomSheet(onDismissRequest = { showReportSheet = false })
    }
}
