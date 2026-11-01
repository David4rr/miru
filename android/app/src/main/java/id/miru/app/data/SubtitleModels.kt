package id.miru.app.data

data class ExternalSubtitleSource(
    val source: String,
    val filename: String,
    val format: String,
    val lang: String,
    val label: String,
    val downloadUrl: String
)

data class AiProviderConfig(
    val providerType: String,
    val baseUrl: String?,
    val apiKey: String?,
    val modelId: String,
    val promptTemplate: String?,
    val isActive: Boolean
)

data class AiFansubResponse(
    val cached: Boolean,
    val cuesTranslated: Int,
    val durationMs: Long,
    val vttContent: String
)

data class LexiconRuleItem(
    val id: Long,
    val targetTerm: String,
    val replacement: String,
    val category: String,
    val isRegex: Boolean
)

fun SubtitleTrack.withOffset(offsetMs: Int): SubtitleTrack {
    val base = url.replace("[?&]offset_ms=[^&]+".toRegex(), "")
    val delim = if (base.contains("?")) "&" else "?"
    return copy(url = "$base${delim}offset_ms=$offsetMs")
}
