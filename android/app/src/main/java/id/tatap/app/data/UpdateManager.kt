package id.tatap.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import id.tatap.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object UpdateManager {
    private const val GITHUB_REPO = "David4rr/tatap"
    private const val API_LATEST = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val API_RELEASES = "https://api.github.com/repos/$GITHUB_REPO/releases?per_page=15"

    val currentVersion: String = try {
        BuildConfig.VERSION_NAME
    } catch (_: Exception) {
        "0.1.5"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isVersionNewer(remoteTag: String, current: String = currentVersion): Boolean {
        val rClean = remoteTag.trimStart('v', 'V').split("-")[0].trim()
        val cClean = current.trimStart('v', 'V').split("-")[0].trim()
        val rParts = rClean.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = cClean.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(rParts.size, cParts.size)) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    suspend fun checkLatestUpdate(): Result<AppRelease?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(API_LATEST)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Tatap-Android-App")
                .get()
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    if (resp.code == 404 || resp.code == 403) {
                        return@runCatching null
                    }
                    throw Exception("Gagal memeriksa pembaruan: HTTP ${resp.code}")
                }
                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                parseReleaseObject(json)
            }
        }
    }

    suspend fun fetchChangelogHistory(): Result<List<AppRelease>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(API_RELEASES)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Tatap-Android-App")
                .get()
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    // Fallback to embedded releases on network/rate-limit error
                    return@use getEmbeddedChangelogs()
                }
                val bodyStr = resp.body?.string().orEmpty()
                val jsonArray = JSONArray(bodyStr)
                val list = mutableListOf<AppRelease>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    parseReleaseObject(obj)?.let { list.add(it) }
                }
                if (list.isEmpty()) getEmbeddedChangelogs() else list
            }
        }.recover {
            getEmbeddedChangelogs()
        }
    }

    private fun parseReleaseObject(json: JSONObject): AppRelease? {
        val tagName = json.optString("tag_name", "").trim()
        if (tagName.isEmpty()) return null
        val title = json.optString("name", tagName)
        val body = json.optString("body", "Tidak ada catatan rilis tambahan.")
        val publishedAt = json.optString("published_at", "")
        val versionName = tagName.trimStart('v', 'V')

        var downloadUrl: String? = null
        var apkSize = 0L

        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    downloadUrl = asset.optString("browser_download_url", "")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
        }

        val isNewer = isVersionNewer(tagName, currentVersion)

        return AppRelease(
            tagName = tagName,
            versionName = versionName,
            title = title,
            body = body,
            publishedAt = publishedAt,
            downloadUrl = downloadUrl,
            apkSize = apkSize,
            isNewer = isNewer
        )
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        targetFileName: String = "tatap-update.apk",
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Tatap-Android-App")
                .get()
                .build()

            val call = httpClient.newCall(request)
            val response = call.execute()
            if (!response.isSuccessful) {
                throw Exception("Gagal mengunduh berkas APK (HTTP ${response.code})")
            }

            val body = response.body ?: throw Exception("Respons server kosong")
            val totalBytes = body.contentLength()

            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val updateDir = File(cacheDir, "updates").apply { if (!exists()) mkdirs() }
            val apkFile = File(updateDir, targetFileName)
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = if (totalBytes > 0) totalRead.toFloat() / totalBytes else 0f
                        onProgress(progress, totalRead, totalBytes)
                    }
                    output.flush()
                }
            }

            apkFile
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun getEmbeddedChangelogs(): List<AppRelease> {
        return listOf(
            AppRelease(
                tagName = "v0.1.5",
                versionName = "0.1.5",
                title = "Tatap Android v0.1.5 (Auto-Update & In-App Changelog)",
                body = """
                    ### Tatap Android v0.1.5
                    
                    #### 🚀 Menu Auto-Update & Changelog Baru:
                    - **In-App Auto-Update Checker:** Periksa pembaruan versi terbaru Tatap langsung dari aplikasi dengan visual status yang jelas dan intuitif.
                    - **Unduh & Pasang Otomatis:** Download pembaruan APK langsung di dalam aplikasi dengan progress bar real-time (MB & %) dan luncurkan pemasang paket Android dengan satu sentuhan.
                    - **Catatan Rilis Lengkap (Changelog):** Riwayat perubahan dari setiap versi kini dapat dibaca kapan saja, lengkap dengan daftar fitur, perbaikan bug, dan tanggal rilis.
                    - **Halaman Pengaturan Baru:** Tab Pengaturan terintegrasi untuk mengelola pembaruan aplikasi, status core daemon, dan pembersihan cache tontonan.
                """.trimIndent(),
                publishedAt = "2026-10-27",
                downloadUrl = "https://github.com/David4rr/tatap/releases/download/v0.1.5/Tatap-v0.1.5-debug.apk",
                apkSize = 44200000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.1.4",
                versionName = "0.1.4",
                title = "Tatap Android v0.1.4 (Major UI/UX Overhaul & Player Polish)",
                body = """
                    ### Tatap Android v0.1.4
                    
                    #### 🎬 Pembaruan Layar Pemutar Video (Player Screen):
                    - Menghilangkan bug tombol play menimpa spinner loading saat menghubungkan stream.
                    - Transisi spinner halus saat rebuffering video di tengah pemutaran.
                    - Double-Tap Seek Visual Feedback (+10s / -10s) dengan animasi pil yang memudar.
                    - Gesture Swipe layar untuk kecerahan (kiri) dan volume (kanan) dengan HUD pill.
                    - Episode Picker Modal Bottom Sheet langsung di dalam player untuk berpindah episode instan.
                    - Tombol Next Episode tepat di samping seekbar.
                    - OLED Modal Bottom Sheet modern untuk pemilihan takarir dan kecepatan putar.
                    
                    #### 🏠 Beranda & Detail:
                    - Auto-scrolling Hero Carousel dengan animasi indikator pil dinamis.
                    - Lencana peringkat tren Indonesia (#1, #2, #3).
                    - Tombol aksi cerdas "Lanjutkan Menonton (Episode X)".
                    - Watch progress bar pada daftar episode dan animasi ekspansi sinopsis.
                    - Persistent genre chips di halaman pencarian dan hapus riwayat individual di koleksi.
                """.trimIndent(),
                publishedAt = "2026-10-27",
                downloadUrl = "https://github.com/David4rr/tatap/releases/download/v0.1.4/Tatap-v0.1.4-debug.apk",
                apkSize = 44145000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.1.3",
                versionName = "0.1.3",
                title = "Tatap Android v0.1.3 (Subtitle Proxying & 403 Forbidden Fix)",
                body = """
                    ### Tatap Android v0.1.3
                    - Mem-proxy seluruh trek takarir (subtitles) melalui daemon lokal dengan injeksi header Referer untuk mencegah error 403 Forbidden dari CDN upstream.
                    - Fallback fail-safe WebVTT kosong dengan HTTP 200 OK agar video player tidak pernah crash saat takarir gagal dimuat.
                    - Forwarding header HTTP Range untuk video seeking 206 Partial Content.
                """.trimIndent(),
                publishedAt = "2026-10-26",
                downloadUrl = "https://github.com/David4rr/tatap/releases/download/v0.1.3/Tatap-v0.1.3-debug.apk",
                apkSize = 44000000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.1.2",
                versionName = "0.1.2",
                title = "Tatap Android v0.1.2 (Buffer Stall Fix & Status Bar Padding)",
                body = """
                    ### Tatap Android v0.1.2
                    - Mengatasi jeda buffering (buffer stall) pada stream video HLS dengan persistent HTTP/2 connection pooling.
                    - Low-latency ExoPlayer LoadControl dengan buffer awal 500ms.
                    - Padding status bar dan navigation bar pada layar detail.
                """.trimIndent(),
                publishedAt = "2026-10-26",
                downloadUrl = "https://github.com/David4rr/tatap/releases/download/v0.1.2/Tatap-v0.1.2-debug.apk",
                apkSize = 43900000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.1.1",
                versionName = "0.1.1",
                title = "Tatap Android v0.1.1 (Fullscreen & Native Stream Resolver)",
                body = """
                    ### Tatap Android v0.1.1
                    - Immersive Fullscreen otomatis menyembunyikan status bar dan bar navigasi saat menonton anime.
                    - Deobfuscasi stream ZokoAnime ultra-cepat (<0.4 detik).
                """.trimIndent(),
                publishedAt = "2026-10-26",
                downloadUrl = "https://github.com/David4rr/tatap/releases/download/v0.1.1/Tatap-v0.1.1-debug.apk",
                apkSize = 43800000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.1.0",
                versionName = "0.1.0",
                title = "Tatap Android v0.1.0 (Initial Release)",
                body = """
                    ### Tatap Android v0.1.0
                    - Rilis perdana aplikasi Tatap Anime Streaming untuk Android.
                    - Core engine Rust Axum tertanam via JNI (`127.0.0.1:8767`).
                    - Antarmuka Native Jetpack Compose bernuansa Crunchyroll OLED.
                """.trimIndent(),
                publishedAt = "2026-10-26",
                downloadUrl = "https://github.com/David4rr/tatap/releases/download/v0.1.0/Tatap-v0.1.0-debug.apk",
                apkSize = 43700000L,
                isNewer = false
            )
        )
    }
}
