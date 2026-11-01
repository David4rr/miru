package id.miru.app.ui.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import id.miru.app.data.ApiClient
import id.miru.app.data.AudioMode
import id.miru.app.data.StreamProviderItem
import id.miru.app.data.StreamServerItem
import id.miru.app.data.getStreamProviders
import id.miru.app.data.getStreamServers

class PlayerSourceState(
    initialProviderId: String = "hianime",
    initialAudioMode: AudioMode = AudioMode.SUB
) {
    var showSourceSheet by mutableStateOf(false)
    var providers by mutableStateOf<List<StreamProviderItem>>(emptyList())
    var currentProviderId by mutableStateOf(initialProviderId)
    var currentServerId by mutableStateOf<String?>(null)
    var servers by mutableStateOf<List<StreamServerItem>>(emptyList())
    var audioMode by mutableStateOf(initialAudioMode)

    suspend fun refreshProviders() {
        ApiClient.getStreamProviders().onSuccess { list ->
            providers = list
            if (providers.none { it.id == currentProviderId }) {
                currentProviderId = list.firstOrNull { it.isDefault }?.id ?: list.firstOrNull()?.id ?: "hianime"
            }
        }
    }

    suspend fun refreshServers(slug: String, episode: Int) {
        ApiClient.getStreamServers(currentProviderId, slug, episode, audioMode.key).onSuccess { list ->
            servers = list
            if (currentServerId == null || servers.none { it.serverId == currentServerId }) {
                currentServerId = list.firstOrNull()?.serverId
            }
        }
    }
}

@Composable
fun rememberPlayerSourceState(
    initialProviderId: String = "hianime",
    initialAudioMode: AudioMode = AudioMode.SUB
): PlayerSourceState {
    return remember { PlayerSourceState(initialProviderId, initialAudioMode) }
}
