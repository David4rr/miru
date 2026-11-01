package id.miru.app.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miru.app.data.AudioMode
import id.miru.app.data.StreamProviderItem
import id.miru.app.data.StreamServerItem
import id.miru.app.ui.components.detail.DetailDubSubToggle
import id.miru.app.ui.theme.CrunchyOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSourceSheet(
    providers: List<StreamProviderItem>,
    currentProviderId: String,
    currentServerId: String?,
    servers: List<StreamServerItem>,
    audioMode: AudioMode,
    onProviderChange: (String) -> Unit,
    onServerSelect: (String) -> Unit,
    onAudioModeChange: (AudioMode) -> Unit,
    onDismiss: () -> Unit
) {
    val activeProv = providers.find { it.id == currentProviderId } ?: providers.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1117),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF333A4A)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = CrunchyOrange, modifier = Modifier.size(20.dp))
                    Text("Streaming Source & Mirror", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8C95A6))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("PROVIDER", color = Color(0xFF758095), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                providers.forEach { prov ->
                    val isSel = prov.id == currentProviderId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) CrunchyOrange.copy(alpha = 0.2f) else Color(0xFF161922))
                            .border(1.dp, if (isSel) CrunchyOrange else Color(0xFF262C3A), RoundedCornerShape(10.dp))
                            .clickable { onProviderChange(prov.id) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(prov.name.split(" ").firstOrNull() ?: prov.name, color = if (isSel) Color.White else Color(0xFFC0C7D5), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(prov.subtitle, color = if (isSel) CrunchyOrange else Color(0xFF6B7280), fontSize = 10.sp)
                        }
                    }
                }
            }

            if (activeProv?.hasDub == true) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("AUDIO TRACK", color = Color(0xFF758095), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    DetailDubSubToggle(currentMode = audioMode, onModeSelected = onAudioModeChange, hasDub = true)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("SERVER MIRRORS", color = Color(0xFF758095), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 200.dp)) {
                items(servers) { srv ->
                    val isSelected = srv.serverId == currentServerId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF1E2330) else Color(0xFF141720))
                            .border(1.dp, if (isSelected) CrunchyOrange.copy(alpha = 0.6f) else Color(0xFF222634), RoundedCornerShape(8.dp))
                            .clickable { onServerSelect(srv.serverId) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(srv.serverName, color = if (isSelected) Color.White else Color(0xFFBAC2D1), fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (srv.latencyMs > 0) Text("${srv.latencyMs}ms", color = Color(0xFF10B981), fontSize = 11.sp)
                            if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = CrunchyOrange, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
