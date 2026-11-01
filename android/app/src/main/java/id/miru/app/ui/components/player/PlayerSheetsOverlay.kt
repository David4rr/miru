package id.miru.app.ui.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import id.miru.app.data.AudioMode
import id.miru.app.data.StreamProviderItem
import id.miru.app.data.StreamServerItem
import id.miru.app.data.SubtitleTrack
import id.miru.app.data.VideoVariant

@Composable
fun PlayerSheetsOverlay(
    showEpisodeSheet: Boolean,
    onDismissEpisodeSheet: () -> Unit,
    totalEpisodes: Int,
    currentEpisode: Int,
    onSelectEpisode: (Int) -> Unit,
    showSubtitleSheet: Boolean,
    onDismissSubtitleSheet: () -> Unit,
    subtitles: List<SubtitleTrack>,
    selectedSubtitle: SubtitleTrack?,
    onSelectSubtitle: (SubtitleTrack?) -> Unit,
    showQualitySheet: Boolean,
    onDismissQualitySheet: () -> Unit,
    variants: List<VideoVariant>,
    selectedVariant: VideoVariant?,
    onSelectVariant: (VideoVariant?) -> Unit,
    animeTitle: String = "",
    slug: String = "",
    onSubtitleOffsetApplied: (Int) -> Unit = {},
    showSourceSheet: Boolean = false,
    onDismissSourceSheet: () -> Unit = {},
    providers: List<StreamProviderItem> = emptyList(),
    currentProviderId: String = "hianime",
    currentServerId: String? = null,
    servers: List<StreamServerItem> = emptyList(),
    audioMode: AudioMode = AudioMode.SUB,
    onProviderChange: (String) -> Unit = {},
    onServerSelect: (String) -> Unit = {},
    onAudioModeChange: (AudioMode) -> Unit = {}
) {
    var showOffsetSheet by remember { mutableStateOf(false) }

    if (showEpisodeSheet) {
        PlayerEpisodeSheet(
            totalEpisodes = totalEpisodes,
            currentEpisode = currentEpisode,
            onSelectEpisode = onSelectEpisode,
            onDismissRequest = onDismissEpisodeSheet
        )
    }

    if (showSubtitleSheet) {
        PlayerSubtitleSheet(
            subtitles = subtitles,
            selectedSubtitle = selectedSubtitle,
            animeTitle = animeTitle,
            episodeNum = currentEpisode,
            onSelectSubtitle = onSelectSubtitle,
            onOpenOffsetSheet = { showOffsetSheet = true },
            onDismissRequest = onDismissSubtitleSheet
        )
    }

    if (showQualitySheet) {
        PlayerQualitySheet(
            variants = variants,
            selectedVariant = selectedVariant,
            onSelectVariant = onSelectVariant,
            onDismissRequest = onDismissQualitySheet
        )
    }

    if (showOffsetSheet) {
        PlayerSubtitleOffsetSheet(
            slug = slug,
            episode = currentEpisode,
            onOffsetChanged = onSubtitleOffsetApplied,
            onSaveOffset = onSubtitleOffsetApplied,
            onDismissRequest = { showOffsetSheet = false }
        )
    }

    if (showSourceSheet) {
        PlayerSourceSheet(
            providers = providers,
            currentProviderId = currentProviderId,
            currentServerId = currentServerId,
            servers = servers,
            audioMode = audioMode,
            onProviderChange = onProviderChange,
            onServerSelect = onServerSelect,
            onAudioModeChange = onAudioModeChange,
            onDismiss = onDismissSourceSheet
        )
    }
}
