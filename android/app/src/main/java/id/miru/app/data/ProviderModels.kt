package id.miru.app.data

data class StreamProviderItem(
    val id: String,
    val name: String,
    val hasSub: Boolean = true,
    val hasDub: Boolean = false,
    val isDefault: Boolean = false
) {
    val subtitle: String
        get() = when {
            hasSub && hasDub -> "SUB & DUB"
            hasSub -> "Hardsub ID"
            else -> "Audio"
        }
}

data class StreamServerItem(
    val serverId: String,
    val serverName: String,
    val latencyMs: Long = 0L
)

enum class AudioMode(val key: String, val label: String) {
    SUB("sub", "SUB"),
    DUB("dub", "DUB");

    companion object {
        fun fromKey(key: String?): AudioMode = when (key?.lowercase()) {
            "dub" -> DUB
            else -> SUB
        }
    }
}

data class StreamSourceSelection(
    val providerId: String,
    val serverId: String? = null,
    val audioMode: AudioMode = AudioMode.SUB
)
