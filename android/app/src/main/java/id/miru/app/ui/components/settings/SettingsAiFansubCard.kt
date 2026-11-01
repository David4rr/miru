package id.miru.app.ui.components.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import id.miru.app.data.AiProviderConfig
import id.miru.app.data.ApiClientSubtitles
import id.miru.app.ui.theme.CrunchyrollOrange
import id.miru.app.ui.theme.SurfaceDark
import id.miru.app.ui.theme.TextMuted
import id.miru.app.ui.theme.TextPrimary
import id.miru.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsAiFansubCard(
    onOpenLexicon: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val providers = remember { listOf("gemini", "groq", "openai", "ollama") }

    var selectedProvider by remember { mutableStateOf("gemini") }
    var apiKey by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("gemini-1.5-flash") }
    var baseUrl by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(false) }
    var allConfigs by remember { mutableStateOf<List<AiProviderConfig>>(emptyList()) }

    LaunchedEffect(Unit) {
        ApiClientSubtitles.getAiProviders().onSuccess { list ->
            allConfigs = list
            list.find { it.providerType.equals(selectedProvider, true) }?.let { cfg ->
                apiKey = cfg.apiKey.orEmpty(); modelId = cfg.modelId
                baseUrl = cfg.baseUrl.orEmpty(); isActive = cfg.isActive
            }
        }
    }

    fun onSelect(type: String) {
        selectedProvider = type
        val existing = allConfigs.find { it.providerType.equals(type, true) }
        apiKey = existing?.apiKey.orEmpty()
        modelId = existing?.modelId ?: if (type == "gemini") "gemini-1.5-flash" else if (type == "groq") "llama-3.3-70b-versatile" else if (type == "openai") "gpt-4o-mini" else "qwen2.5:7b"
        baseUrl = existing?.baseUrl.orEmpty()
        isActive = existing?.isActive ?: false
    }

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Fansub Engine (BYOK)", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Takarir cerdas via LLM pribadi", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(checked = isActive, onCheckedChange = { isActive = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CrunchyrollOrange))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                providers.forEach { p ->
                    val isSel = selectedProvider.equals(p, true)
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) CrunchyrollOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                            .border(1.dp, if (isSel) CrunchyrollOrange else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { onSelect(p) }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(p.uppercase(), color = if (isSel) CrunchyrollOrange else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key", fontSize = 12.sp) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrunchyrollOrange, focusedLabelColor = CrunchyrollOrange)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = modelId, onValueChange = { modelId = it }, label = { Text("Model ID", fontSize = 12.sp) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrunchyrollOrange, focusedLabelColor = CrunchyrollOrange)
            )
            if (selectedProvider == "ollama" || baseUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL", fontSize = 12.sp) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrunchyrollOrange, focusedLabelColor = CrunchyrollOrange)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenLexicon, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))) {
                    Text("Kamus Anime", color = TextPrimary, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        scope.launch {
                            val config = AiProviderConfig(selectedProvider, baseUrl.trim().takeIf { it.isNotEmpty() }, apiKey.trim().takeIf { it.isNotEmpty() }, modelId.trim(), null, isActive)
                            ApiClientSubtitles.saveAiProvider(config).fold(
                                onSuccess = {
                                    Toast.makeText(context, "Konfigurasi $selectedProvider tersimpan!", Toast.LENGTH_SHORT).show()
                                    allConfigs = allConfigs.filterNot { it.providerType.equals(selectedProvider, true) } + config
                                },
                                onFailure = { Toast.makeText(context, "Gagal: ${it.message}", Toast.LENGTH_SHORT).show() }
                            )
                        }
                    },
                    modifier = Modifier.weight(1.2f).height(40.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = CrunchyrollOrange)
                ) {
                    Text("Simpan", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
