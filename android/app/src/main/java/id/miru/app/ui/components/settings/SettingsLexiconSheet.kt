package id.miru.app.ui.components.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import id.miru.app.data.ApiClientSubtitles
import id.miru.app.data.LexiconRuleItem
import id.miru.app.ui.theme.CrunchyOrange
import id.miru.app.ui.theme.DarkBackground
import id.miru.app.ui.theme.DarkSurface
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsLexiconSheet(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var rules by remember { mutableStateOf<List<LexiconRuleItem>>(emptyList()) }
    var termInput by remember { mutableStateOf("") }
    var replacementInput by remember { mutableStateOf("") }

    fun refreshRules() {
        scope.launch {
            ApiClientSubtitles.getLexiconRules().onSuccess { rules = it }
        }
    }

    LaunchedEffect(Unit) { refreshRules() }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = DarkSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Book, contentDescription = null, tint = CrunchyOrange, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Kamus Istilah Anime (Universal Lexicon)", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text("Kata yang dilindungi agar tidak salah diterjemahkan (contoh: Bankai, Jutsu)", color = TextMuted, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = termInput, onValueChange = { termInput = it }, label = { Text("Istilah", fontSize = 11.sp) },
                    singleLine = true, modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrunchyOrange, focusedLabelColor = CrunchyOrange)
                )
                OutlinedTextField(
                    value = replacementInput, onValueChange = { replacementInput = it }, label = { Text("Pengganti", fontSize = 11.sp) },
                    singleLine = true, modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrunchyOrange, focusedLabelColor = CrunchyOrange)
                )
                Button(
                    onClick = {
                        if (termInput.isBlank() || replacementInput.isBlank()) return@Button
                        scope.launch {
                            ApiClientSubtitles.saveLexiconRule(termInput.trim(), replacementInput.trim()).fold(
                                onSuccess = { termInput = ""; replacementInput = ""; refreshRules() },
                                onFailure = { Toast.makeText(context, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show() }
                            )
                        }
                    },
                    modifier = Modifier.height(56.dp).align(Alignment.CenterVertically),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrunchyOrange)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah", tint = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DarkBackground).padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(rule.targetTerm, color = CrunchyOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("-> ${rule.replacement}", color = TextMuted, fontSize = 12.sp)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                ApiClientSubtitles.deleteLexiconRule(rule.id).onSuccess { refreshRules() }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
