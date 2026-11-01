package id.miru.app.ui.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.ApiClient
import id.miru.app.data.getSafeMode
import id.miru.app.data.setSafeMode
import id.miru.app.ui.theme.CrunchyrollOrange
import id.miru.app.ui.theme.SurfaceDark
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary
import id.miru.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsSafeModeCard(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var isSafeMode by remember { mutableStateOf(true) }
    var showWarningDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ApiClient.getSafeMode().onSuccess { isSafeMode = it }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                text = "KONTROL KONTEN",
                color = CrunchyrollOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mode Aman (Safe Mode)",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isSafeMode) "Menyembunyikan konten dewasa (18+) dari katalog & pencarian"
                        else "Konten dewasa (18+) diizinkan muncul",
                        color = if (isSafeMode) TextMuted else Color(0xFFFF7043),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isSafeMode,
                    onCheckedChange = { checked ->
                        if (!checked) {
                            showWarningDialog = true
                        } else {
                            isSafeMode = true
                            scope.launch { ApiClient.setSafeMode(true) }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CrunchyrollOrange,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0xFF2E303B)
                    )
                )
            }
        }
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = {
                Text(text = "Nonaktifkan Mode Aman?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Konten dewasa dan eksplisit (18+) mungkin akan muncul di pencarian dan katalog. Anda yakin ingin melanjutkan?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            containerColor = SurfaceDark,
            confirmButton = {
                Button(
                    onClick = {
                        isSafeMode = false
                        showWarningDialog = false
                        scope.launch { ApiClient.setSafeMode(false) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text(text = "Nonaktifkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningDialog = false }) {
                    Text(text = "Batal", color = TextMuted)
                }
            }
        )
    }
}
