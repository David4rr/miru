package id.miru.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import id.miru.app.BuildConfig
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
    private const val GITHUB_REPO = "David4rr/miru"
    private const val API_LATEST = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val API_RELEASES = "https://api.github.com/repos/$GITHUB_REPO/releases?per_page=15"

    val currentVersion: String = try {
        BuildConfig.VERSION_NAME
    } catch (_: Exception) {
        "0.2.15"
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
                .header("User-Agent", "Miru-Android-App")
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
                .header("User-Agent", "Miru-Android-App")
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
        targetFileName: String = "miru-update.apk",
        onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Miru-Android-App")
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
                tagName = "v0.2.15",
                versionName = "0.2.15",
                title = "Miru Android v0.2.15 (Canon Franchise Ordering & WIB Airing Schedule Accuracy)",
                body = """
                    ### Miru Android v0.2.15

                    #### 🎬 Perbaikan Urutan Edisi & Relasi Franchise Kanon:
                    - **Pembersihan Relasi Non-Kanon:** Menghapus relasi longgar `PARENT`, `SIDE_STORY`, `ALTERNATIVE`, dan `SPIN_OFF` pada `fetch_anime_editions`, strictly membatasi traversal hanya pada `PREQUEL` dan `SEQUEL` langsung.
                    - **Eliminasi Pengelompokan Palsu:** Anime mandiri (seperti *Fullmetal Alchemist: Brotherhood*) dan spin-off mandiri (seperti *KonoSuba: Bakuen*) tidak lagi dikelompokkan dengan anime lain atau diberi label musim `S1`/`S2` yang keliru. Komponen edisi otomatis tersembunyi jika anime berdiri sendiri (`editions.size <= 1`).
                    - **Ekspansi Frontier Waralaba Panjang:** Menambahkan traversal ekspansi maju dan mundur terukur (2 iterasi) sehingga serial panjang (>3 musim seperti *My Hero Academia*) tetap terpetakan lengkap tanpa terpotong batas kedalaman GraphQL AniList.
                    - **Cache SQLite 24 Jam:** Menyimpan hasil resolusi edisi ke SQLite cache selama 24 jam untuk navigasi instan tanpa overhead network berulang.

                    #### ⏰ Perbaikan Jadwal Rilis & Jam Tayang WIB (Waktu Indonesia Barat):
                    - **Perhitungan Batas Hari WIB (UTC+7):** Mengganti kalkulasi hari UTC dengan offset WIB (+25.200 detik) pada jadwal mingguan dan catalog `Rilis Hari Ini`, mengeliminasi kebocoran episode dini hari esok (misal 06:00 WIB) yang sebelumnya muncul di bawah jadwal malam hari ini.
                    - **Pengurutan Jam Kronologis Presisi:** Mengurutkan seluruh penayangan harian secara ascending berdasarkan waktu tayang riil, memastikan urutan jam mengalir teratur dari pagi hingga tengah malam tanpa urutan jam terbalik.
                    - **Badge Status Penayangan & Jam Rilis:** Kartu jadwal kini menampilkan badge status `"Sudah Tayang"` / `"Akan Tayang"`, serta swimlane beranda menampilkan jam penayangan ("21:00 WIB") untuk episode mendatang dan status rilis ("Ep X • Rilis") untuk episode yang sudah tayang.
                    - **Timezone Eksplisit Android:** Memastikan `SimpleDateFormat` memformat jam dengan timezone eksplisit `Asia/Jakarta` mencegah inkonsistensi waktu pada perangkat dengan setelan non-WIB.
                """.trimIndent(),
                publishedAt = "2026-10-30",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.15/Miru-v0.2.15-release.apk",
                apkSize = 22900000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.2.14",
                versionName = "0.2.14",
                title = "Miru Android v0.2.14 (Arabic Subtitle Fix, Player Subtitle Selector, 5x Stream Loading & 120Hz Parallax)",
                body = """
                    ### Miru Android v0.2.14

                    #### 🌐 Perbaikan Subtitle Arab & Pemilih Trek Takarir (Subtitle Selector):
                    - **Normalisasi Kode Bahasa (`normalize_sub_lang`):** Memperbaiki kesalahan deteksi takarir pada anime seperti Jujutsu Kaisen dan Chainsaw Man di mana embed server melabeli seluruh takarir (termasuk Arab) sebagai `lang="en"`.
                    - **Kandidat Terjemahan Bahasa Inggris Asli:** Memastikan auto-translate Bahasa Indonesia hanya mengambil trek Bahasa Inggris asli dan mengecualikan bahasa lain.
                    - **Dukungan Deteksi Bahasa Otomatis (`sl=auto`):** Meng-upgrade engine terjemahan Google GTX+ dengan deteksi bahasa sumber otomatis untuk menangani seluruh bahasa upstream.
                    - **Modal Pemilih Takarir (`PlayerSubtitleSheet`):** Menyediakan bottom sheet pemilih trek takarir di pemutar video dengan badge bahasa (`ID`, `EN`, dll.) dan opsi menonaktifkan subtitle.
                    - **Ikon Toggle Subtitle di HUD:** Menambahkan tombol subtitle di sebelah tombol kunci layar dengan indikator status aktif oranye Crunchyroll.
                    - **Pergantian Trek Real-Time (`switchSubtitleTrack`):** Menukar konfigurasi takarir ExoPlayer di posisi playback berjalan tanpa buffering ulang dan mencegah socket starvation.

                    #### ⚡ Akselerasi Loading Streaming 5x Lebih Cepat:
                    - **Paralelisasi Terjemahan dengan Tokio `JoinSet`:** Mengganti loop sekuensial 20 request di daemon Rust menjadi tugas asinkron simultan (batch 45 cues), memangkas waktu proses dari 13.2 detik menjadi < 1 detik.
                    - **Background Subtitle Pre-Warming:** Memulai proses terjemahan dan caching di background secara non-blocking saat stream di-resolve, sehingga subtitle sudah siap di SQLite cache saat diminta player.
                    - **Optimalisasi Jaringan & TCP No-Delay:** Mengaktifkan `tcp_nodelay(true)` dan connection pool 20 koneksi pada client proxy Rust untuk transmisi segmen video tanpa jeda paket.
                    - **Tuning Buffer ExoPlayer:** Menurunkan ambang batas playback ke 1000ms dengan time prioritization untuk pemutaran video instan.

                    #### 🖼️ Peningkatan Efek Paralaks Beranda (120Hz Home Parallax):
                    - **Eliminasi Deadzone:** Memperbaiki perhitungan offset agar paralaks bergerak mulus sejak pixel pertama tanpa jeda 140px dari header.
                    - **Rendering Draw-Phase Tanpa Recomposition:** Menggunakan lambda provider `parallaxOffsetProvider: () -> Float` agar translasi GPU berjalan mulus di layar 90Hz/120Hz.
                    - **Kedalaman Visual 3x Lebih Nyata:** Memperluas skala bleed ke 1.20x dan meningkatkan batas translasi vertikal hingga 150px.
                """.trimIndent(),
                publishedAt = "2026-10-30",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.14/Miru-v0.2.14-release.apk",
                apkSize = 22800000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.2.13",
                versionName = "0.2.13",
                title = "Miru Android v0.2.13 (Bookmark History Sync, In-Progress Resume CTA & Double-Back Exit)",
                body = """
                    ### Miru Android v0.2.13

                    #### 🔖 Sinkronisasi Riwayat Tontonan dari Bookmark:
                    - **Pencatatan Otomatis saat Memutar Bookmark:** Menyambungkan pemanggilan `ApiClient.addHistory` saat stream siap diputar pada `PlayerScreen`, menjamin anime yang ditonton dari bookmark langsung masuk ke Riwayat Tontonan.
                    - **Ketahanan Database & Metadata Fallback:** Mengoptimalkan `save_progress` di Rust core daemon (`core/src/db.rs`) dengan pembaruan otomatis timestamp `played_at = CURRENT_TIMESTAMP`, lookup metadata ke tabel `bookmarks`, serta dukungan metadata judul, poster, dan tipe anime.
                    - **Sortir Riwayat Deterministik:** Menambahkan tie-breaker `id DESC` pada query `get_history` untuk memecah timestamp yang identik dalam detik yang sama.

                    #### 🎬 Peningkatan Tombol Lanjutkan Menonton (CTA Resume):
                    - **Deteksi Progres Episode 1:** Mengubah logika tombol utama di `DetailHeroHeader` dan `DetailScreen` agar menampilkan "Lanjutkan Menonton (Episode 1)" saat episode 1 sedang dalam progres (> 0%), bukan hanya untuk episode > 1.
                    - **Pembaruan Instan Tab Library:** Menambahkan auto-refresh saat berpindah tab antara Bookmark dan Riwayat pada `LibraryScreen` agar riwayat tontonan langsung terupdate secara real-time.

                    #### 🛡️ Proteksi Keluar Aplikasi (Double-Back Press):
                    - **Konfirmasi Dua Kali Tekan:** Mencegah aplikasi tertutup tanpa sengaja dengan mewajibkan dua kali penekanan tombol back dalam kurun waktu 2 detik disertai notifikasi Toast ("Tekan sekali lagi untuk keluar").
                    - **Reset Otomatis Timer:** Timer tombol back otomatis di-reset saat pengguna berpindah tab atau berpindah layar (detail, player, jadwal).
                """.trimIndent(),
                publishedAt = "2026-10-29",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.13/Miru-v0.2.13-release.apk",
                apkSize = 22700000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.2.12",
                versionName = "0.2.12",
                title = "Miru Android v0.2.12 (Japanese Kanji Identity & Ultra-Smooth Parallax Fix)",
                body = """
                    ### Miru Android v0.2.12

                    #### 🇯🇵 Identitas Resmi Logo Miru (Japanese Kanji 見):
                    - **Pembaruan Menyeluruh Ikon Aplikasi:** Menggantikan ikon lama secara tuntas pada seluruh density mipmap (`mdpi` hingga `xxxhdpi`), ikon adaptif Android (`ic_launcher_foreground`), serta vektor `ic_miru_logo`.
                    - **Integrasi Logo di Header Utama:** Menampilkan mark logo resmi karakter Jepang **見** (*miru*) berpalet Crunchyroll Orange (`#FF6A00`) di samping tipografi MIRU pada `HomeTopHeader` dan animasi splash screen pembuka.

                    #### 🎬 Perbaikan Total Animasi Parallax HomeScreen:
                    - **Pelacakan Offset Berkelanjutan (Zero Glitch):** Menghapus lompatan nilai offset drastis saat header menggulir keluar layar; kini translasi parallax dihitung secara kontinu dari posisi piksel banner hero (`-heroItem.offset`), menjamin pergerakan 120 FPS tanpa patah-patah.
                    - **Proteksi Bleed Skala (1.15x):** Menambahkan skala 1.15x pada gambar poster/banner di `HeroBannerItem` dan `DetailHeroHeader` agar pergerakan translasi tidak pernah meninggalkan celah kosong hitam di bagian atas kontainer.
                """.trimIndent(),
                publishedAt = "2026-10-29",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.12/Miru-v0.2.12-release.apk",
                apkSize = 22700000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.2.11",
                versionName = "0.2.11",
                title = "Miru Android v0.2.11 (Parallax Home, Smart Recommendations & UI Polish)",
                body = """
                    ### Miru Android v0.2.11

                    #### 🎬 Animasi Parallax HomeScreen:
                    - **Parallax Banner Carousel:** Hero banner pada `HomeScreen` kini dilengkapi translasi parallax dinamis (0.45x scroll offset) dan viewport clipping, menghadirkan konsistensi efek kedalaman sinematik di seluruh layar utama.

                    #### 🎯 Rekomendasi Pintar (Smart Recommendation Engine):
                    - **Personalisasi Berdasarkan Riwayat:** Mesin rekomendasi pintar (`RecommendationEngine`) menganalisis riwayat tontonan dan afinitas genre pengguna, memfilter judul yang sudah ditonton, dan menampilkan swimlane rekomendasi kontekstual ("Rekomendasi Pintar • Untukmu") di `HomeScreen`.

                    #### 🧹 Eliminasi Redundansi & Perapian Detail:
                    - **Konsolidasi Tombol Bookmark:** Menghapus tombol bookmark duplikat di `DetailHeroHeader`, memberikan ruang penuh pada tipografi judul dan mengonsolidasikan aksi penanda buku secara eksklusif ke dalam `DetailTopBar`.

                    #### 📐 Stabilisasi Dimensi Kartu Edisi Franchise:
                    - **Dimensi Seragam (235dp):** Menetapkan kontainer teks tetap (60dp) dengan batas `minLines`/`maxLines` pada judul dan subjudul di `DetailEditionsSection`, memastikan seluruh kartu edisi memiliki tinggi yang presisi tanpa pergeseran baseline.
                """.trimIndent(),
                publishedAt = "2026-10-28",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.11/Miru-v0.2.11-release.apk",
                apkSize = 22700000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.2.10",
                versionName = "0.2.10",
                title = "Miru Android v0.2.10 (Status Bar Insets, Parallax Backdrop, Morphing TopBar & Snappy Minimalism)",
                body = """
                    ### Miru Android v0.2.10

                    #### 📐 Proteksi Status Bar & Inset Edge-to-Edge:
                    - **Eliminasi Tumpang Tindih Status Bar (Status Bar Overlap Fix):** Menambahkan proteksi insets status bar yang menyeluruh pada kontainer navigasi utama dan seluruh tab (`HomeScreen`, `SearchScreen`, `LibraryScreen`, `SettingsScreen`), serta banner pembaruan, sehingga elemen UI tidak lagi tertutup atau menabrak indikator jam/baterai/sinyal Android.
                    - **App Bar Melayang Adaptif di DetailScreen:** Menggantikan tombol back melayang tanpa pelindung dengan `DetailTopBar` yang meminjam status bar padding dan menampilkan scrim gradien serta latar obsidian solid saat digulir, melindungi status bar 100% dari teks atau kartu episode yang bergulir ke atas.

                    #### 🎬 Animasi Parallax & Morfologi Dinamis (Parallax & Morphing Animation):
                    - **Parallax Header Hero:** Banner anime di `DetailScreen` kini bertranslasi dengan efek kedalaman spasial halus (0.45x kecepatan scroll) dan viewport clipping (`clipToBounds()`), menghadirkan nuansa sinematik premium.
                    - **Top App Bar Bermorfologi (Morphing TopBar):** Header hero bertransisi secara mulus menjadi top app bar minimalis yang terkunci di atas saat pengguna menggulir ke bawah, menampilkan judul anime, episode berjalan, dan tombol putar cepat mini yang bermorfologi ke dalam bar.

                    #### ⚡ Tombol Aksi Responsif & Taktil (Snappy Action Button Micro-Interactions):
                    - **Umpan Balik Tekanan Instan:** Menambahkan animasi pegas taktil instan (`scale(0.96f)` dengan `Spring.StiffnessMedium`) pada `OrangeButton` ("Lanjutkan Menonton" / "Mulai Menonton") dan tombol aksi lainnya tanpa latensi visual.
                    - **Akselerasi FAB & Menu Navigasi:** Menaikkan kekakuan pegas (`stiffness`) `FloatingNavFab` dari 400 (`StiffnessMediumLow`) menjadi 1500 (`StiffnessMedium`) dan memangkas jeda sembunyi scroll dari 1200ms ke 600ms untuk pengalaman navigasi yang lincah dan cepat.

                    #### ✨ Desain Minimalis Elegan & Anti-Slop (Minimalist Editorial Aesthetic):
                    - **Pemusnahan Pola AI Slop:** Menghapus bayangan berpendar oranye (`spotColor`), garis tepi gradien neon yang berlebihan, dan titik status dekoratif tanpa fungsi.
                    - **Desain Arsitektural Presisi:** Menerapkan garis tepi hairline 1px halus (`#26262B`), palet warm obsidian matte, dan tipografi berjarak bersih sesuai panduan `minimalist-ui`.
                """.trimIndent(),
                publishedAt = "2026-10-28",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.10/Miru-v0.2.10-release.apk",
                apkSize = 22600000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.2.9",
                versionName = "0.2.9",
                title = "Miru Android v0.2.9 (Eliminate Fake Episodes, Exact Airing Bounds & Franchise Sync)",
                body = """
                    ### Miru Android v0.2.9

                    #### 🎬 Eliminasi Episode Fiktif & Sinkronisasi Penayangan (Fake EP Elimination & Airing Accuracy):
                    - **Penghitungan Episode Riil Berdasarkan Penayangan (`actualEpisodeCount`):** Menghapus fallback asal 12 episode (`episodes ?: 12`) dan membatasi daftar episode secara ketat hanya pada episode yang telah diverifikasi tayang (`episodesReleased`), mencegah tombol episode fiktif untuk anime yang sedang tayang.
                    - **Perlindungan Anime Belum Rilis / Jadwal Mendatang:** Anime berstatus `NOT_YET_RELEASED` (atau `RELEASING` yang episode perdananya belum tayang) kini secara mutlak memiliki 0 episode yang dapat diputar, menampilkan banner informatif jadwal mendatang alih-alih daftar episode kosong atau palsu.
                    - **Tampilan Lencana Penayangan Akurat:** Memperbaiki `displayEpisodes` dan subjudul musim anime agar menampilkan status penayangan terkini (misal: "Ep 4 / 12" atau "Ep 4") alih-alih mengklaim seluruh episode sudah tersedia ("12 Eps").
                    - **Sinkronisasi Episode Lintas Musim / Edisi Franchise:** Menambahkan kueri `nextAiringEpisode` pada relasi waralaba anime dan parsing `episodes_released` pada `AnimeEdition`, sehingga pemilihan musim berbeda pada halaman detail langsung menyajikan jumlah episode yang valid tanpa episode fiktif.
                    - **Pencegahan Loncat ke Episode Fiktif di Pemutar Video:** Memperbaiki batas `hasNextEpisode`, tombol episode berikutnya, dan auto-advance pemutar video agar tidak melompat ke episode mendatang yang belum dirilis saat episode terbaru selesai ditonton.
                    - **Validasi Parameter Resolusi Stream:** Menambahkan validasi ketat `ep < 1` pada API daemon stream untuk menolak permintaan episode tidak valid sebelum memanggil scraper upstream.
                """.trimIndent(),
                publishedAt = "2026-10-28",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.9/Miru-v0.2.9-release.apk",
                apkSize = 22600000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.2.8",
                versionName = "0.2.8",
                title = "Miru Android v0.2.8 (Hotfix: 500 Stream Timeout, Redundant Probing & Failover Overhaul)",
                body = """
                    ### Miru Android v0.2.8

                    #### ⚡ Perbaikan Streaming & Timeout (Streaming Engine & Timeout Fixes):
                    - **Eliminasi Redundant Upstream M3U8 Probe:** Menghapus probe GET upstream ganda pada `master_src` di core Rust daemon yang sebelumnya memakan waktu 2–5 detik dan kerap memicu false-negative timeout.
                    - **Pembersihan Pemblokir Jaringan AniSkip:** Menghapus panggilan sinkronis ke `api.aniskip.com` saat resolusi stream yang memperlambat pemuatan stream 2–4 detik.
                    - **Penanganan Error HiAnime Lebih Tangguh:** Memperbaiki perulangan server scraping agar kegagalan salah satu server embed tidak langsung memutus proses, melainkan otomatis beralih ke server cermin berikutnya.
                    - **Penerusan Petunjuk Judul Akurat (Title Hint):** Menambahkan parameter `title` pada `/api/stream/resolve` agar pencarian fallback HiAnime menggunakan nama asli anime, bukan ekstraksi slug yang terpotong.
                    - **Penanganan Timeout & Error Informatif:** Memperpanjang batas waktu OkHttp Android dari 15s menjadi 35s untuk jaringan seluler, serta membaca detail JSON error dari daemon agar pengguna melihat pesan yang jelas alih-alih HTTP 500 generik.
                """.trimIndent(),
                publishedAt = "2026-10-28",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.8/Miru-v0.2.8-release.apk",
                apkSize = 22600000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.2.7",
                versionName = "0.2.7",
                title = "Miru Android v0.2.7 (Floating Action Button Navigation & Clean Player HUD)",
                body = """
                    ### Miru Android v0.2.7

                    #### 🚀 Fitur Unggulan & Pembaruan UX (Major Features & UX):
                    - **Navigasi Floating Action Button (FAB):** Mengganti bilah navigasi bawah statis dengan Floating Action Button interaktif beranimasi yang mempertahankan seluruh menu utama (Beranda, Jelajah, Koleksi, Pengaturan).
                    - **Responsif Terhadap Gulir (Auto-Hide & Idle-Reappear):** Tombol melayang otomatis menyelinap keluar saat pengguna menggulir layar dan muncul kembali secara halus saat diam/idle untuk tampilan konten yang lebih luas dan imersif.
                    - **Penjelajahan Edge-to-Edge:** Menghilangkan ruang kosong bilah bawah permanen sehingga daftar anime dapat mengalir bebas di balik tombol navigasi.
                    - **Pembersihan Fitur Auto-Skip Intro/Outro:** Menghapus mesin auto-skip pembuka dan penutup yang kerap tidak akurat agar alur video tidak terputus tanpa sengaja.
                    - **Player HUD Bersih & Sinematik:** Menghapus tombol lewati manual dan tombol toggle auto-skip dari HUD pemutar video untuk pengalaman menonton yang bebas gangguan.
                """.trimIndent(),
                publishedAt = "2026-10-28",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.7/Miru-v0.2.7-release.apk",
                apkSize = 22600000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.2.6",
                versionName = "0.2.6",
                title = "Miru Android v0.2.6 (Fix Continue Watching Freeze & Complete Force-Unwrap Eradication)",
                body = """
                    ### Miru Android v0.2.6

                    #### 🐛 Perbaikan Menyeluruh & Kestabilan (Stability & Hotfix):
                    - **Mengatasi Freeze & Crash Saat Tekan Lanjutkan Menonton (Continue Watching):** Memperbaiki loop pemantauan posisi video di `PlayerScreen` yang kekurangan jeda (`delay`), mencegah starvation dan pembekuan Main Thread UI saat masuk ke pemutar dari beranda.
                    - **Pemusnahan Total Force-Unwrap (Zero Null Pointers):** Menghapus seluruh operator `!!` di seluruh codebase (`MainActivity`, `PlayerScreen`, `DetailScreen`, `HomeScreen`, `ScheduleScreen`, `SettingsScreen`) dan menggantikannya dengan safe call pattern (`?.let`, `?:`).
                    - **Penanganan Error Pemutar Lebih Tangguh:** Blok `try-catch` terisolasi pada background poller pemutar untuk menjamin graceful exit saat ExoPlayer dilepaskan.
                """.trimIndent(),
                publishedAt = "2026-10-27",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.6/Miru-v0.2.6-release.apk",
                apkSize = 22600000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.2.5",
                versionName = "0.2.5",
                title = "Miru Android v0.2.5 (Fix Detail & Player Screen Exit Crash)",
                body = """
                    ### Miru Android v0.2.5

                    #### 🐛 Perbaikan Bug Kritis (Critical Bugfix):
                    - **Mengatasi Crash Tombol Kembali di Halaman Detail & Pemutar:** Memperbaiki crash fatal `NullPointerException` saat menekan tombol kembali (Back button / gesture) dari layar Detail anime maupun Pemutar video dengan menyimpan state non-null selama transisi keluar AnimatedContent.
                    - **Transisi Keluar Layar Mulus:** Animasi fade-out saat menutup Detail atau Pemutar video kini berjalan lancar tanpa kedipan atau interupsi.

                    #### 👁️ Fitur Unggulan v0.2.4 Termasuk:
                    - Desain logo resmi "The Chrono Eye", ikon peluncur adaptif, dan splash screen window cold-start.
                    - Penjeda otomatis pemutar di latar belakang untuk mencegah kebocoran audio.
                    - Kunci refresh rate 120Hz dan dok navigasi OLED beranimasi.
                """.trimIndent(),
                publishedAt = "2026-10-27",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.5/Miru-v0.2.5-release.apk",
                apkSize = 22600000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.2.4",
                versionName = "0.2.4",
                title = "Miru Android v0.2.4 (The Chrono Eye Brand Identity, Animated Splash & Player Stability)",
                body = """
                    ### Miru Android v0.2.4

                    #### 👁️ Identitas Brand Resmi ("The Chrono Eye"):
                    - **Master Logo Suite & Desain Vektor Murni:** Peluncuran logo resmi Miru berakar pada konsep "The Chrono Eye" (lensa anime & portal waktu dinamis) dengan geometri presisi dan sudut harmonis.
                    - **Ikon Peluncur Adaptif Android:** Integrasi ikon aplikasi baru untuk seluruh bucket kerapatan (`mipmap-mdpi` hingga `xxxhdpi`) serta XML adaptive icon modern di Android 8.0+.
                    - **Kit Distribusi Lengkap:** Asset master SVG, high-res PNG, favicon web/PWA, dan panduan identitas visual terstandarisasi.

                    #### 🚀 Layar Pembuka Dingin & Animasi (Splash Screen):
                    - **Native Window Cold-Start Splash:** Menerapkan tema startup `Theme.Miru.Splash` dengan latar OLED `#090A0E` dan logo vektor, mengeliminasi kedipan putih saat aplikasi dibuka dingin.
                    - **Compose Animated Splash:** Transisi animasi masuk halus dengan efek skala pegas (spring scale entrance) dan aura pendaran oranye sebelum masuk ke beranda.

                    #### 🛡️ Stabilitas Pemutar & Rotasi Layar (Player Stability):
                    - **Pencegahan Kebocoran Audio di Latar Belakang:** Mengikat ExoPlayer ke `LifecycleEventObserver` agar otomatis menjeda pemutaran dan menyinkronkan progres tonton saat pengguna menekan tombol Home atau berpindah aplikasi.
                    - **Pencegahan Kunci Rotasi Layar (Landscape Lock Fix):** Menyetel ulang orientasi layar dan status bar secara deterministik ke mode bebas (`SCREEN_ORIENTATION_UNSPECIFIED`) setiap kali meninggalkan pemutar video.
                    - **Retensi Sesi Pemutar (SingleTask):** Mengonfigurasi `launchMode="singleTask"` pada Activity utama agar sesi tonton tidak ter-reset saat kembali dari launcher.

                    #### ⚡ Optimasi 120Hz & Fitur Lanjutan:
                    - **Kunci Refresh Rate Tinggi:** Mengunci tampilan ke refresh rate tertinggi perangkat (90Hz / 120Hz / 144Hz).
                    - **Navigasi Bawah OLED:** Dok navigasi hitam pekat dengan indikator pendar oranye dan pantulan skala ikon responsif.
                    - **Smart Skip & Timeline Edisi Anime:** Dukungan lewati intro/outro yang akurat serta daftar urutan musim waralaba (S1-SN).
                """.trimIndent(),
                publishedAt = "2026-10-27",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.4/Miru-v0.2.4-release.apk",
                apkSize = 23700000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.2.3",
                versionName = "0.2.3",
                title = "Miru Android v0.2.3 (120Hz Fluid Motion, Elevated OLED Nav, Smart Skip & Franchise Seasons)",
                body = """
                    ### Miru Android v0.2.3

                    #### ⚡ Performa 120Hz & Transisi Halus (Fluid Motion):
                    - **Dukungan 120Hz High Refresh Rate:** Integrasi API WindowManager DisplayMode untuk mengunci refresh rate layar maksimum perangkat (90Hz / 120Hz / 144Hz) tanpa throttle ke 60Hz.
                    - **Transisi Layar Animasi Lembut:** Mengganti pergantian layar instan dengan AnimatedContent spring dan crossfade lembut saat berpindah layar maupun tab navigasi.
                    - **Optimasi Pemuatan Gambar Coil:** Konfigurasi memori cache 25% RAM, disk cache 150MB, hardware bitmap, dan crossfade halus saat menggulir ribuan poster anime.

                    #### 🎨 Navigasi Bawah OLED Premium (Elevated Dock):
                    - **Kapsul Indikator Aktif Dinamis:** Tab aktif kini dinaungi pill capsule bernuansa Crunchyroll Orange dengan animasi pegas (spring) yang responsif dan memantul.
                    - **Pantulan Ikon Animatif (Scale Bounce):** Ikon tab yang dipilih membesar secara proporsional (1.15x) dengan garis aksen bercahaya yang modern dan tidak membosankan.

                    #### ⏩ Perbaikan Logika Lewati Pembuka & Penutup (Smart Skip):
                    - **Logika Skip Intro & Outro Akurat:** Mengatasi gagal deteksi AniSkip dengan parameter query episode universal, pencarian cerdas judul anime ke MAL ID, dan dukungan auto-skip outro langsung ke episode berikutnya.
                    - **Tombol Lewati Cepat Universal:** Hadir tombol lewati intro (+85 detik standar) dan tombol episode berikutnya otomatis di bagian outro meski anime belum terdaftar di AniSkip.

                    #### 📚 Urutan Musim & Edisi Anime Lengkap (Franchise Timeline):
                    - **Daftar Urutan Musim (S1 - S...):** Menampilkan daftar berurutan kronologis untuk anime dengan banyak musim/edisi (misal Attack on Titan S1-S6, Demon Slayer, MHA) sehingga pengguna tahu urutan menonton yang tepat dari awal.
                    - **Navigasi Edisi Instan:** Sekali sentuh pada edisi yang diinginkan langsung menampilkan episode dan detail anime musim tersebut.
                """.trimIndent(),
                publishedAt = "2026-10-27",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.3/Miru-v0.2.3-release.apk",
                apkSize = 35900000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.2.0",
                versionName = "0.2.0",
                title = "Miru Android v0.2.0 (Brand Renaming, Auto-Skip & Modern Floating Nav)",
                body = """
                    ### Miru Android v0.2.0

                    #### 🚀 Identitas Baru & Optimasi Sistem:
                    - **Rebranding Menjadi Miru:** Bertransisi resmi dari Tatap menjadi Miru (package: id.miru.app, library: libmiru_core.so) untuk identitas yang lebih ringkas, elegan, dan berkarakter.
                    - **Konsistensi Bahasa Indonesia:** Menerjemahkan seluruh label status (Sedang Tayang, Tamat, Belum Rilis), genre anime, dan musim ke bahasa Indonesia baku.
                    - **Pembersih & Penerjemah Sinopsis:** Pembersihan tag HTML otomatis serta integrasi fitur terjemahan sinopsis langsung dari Google GTX di dalam layar detail anime.

                    #### 🎬 Pemutar Video & Fitur UX:
                    - **Auto Skip Intro & Outro (AniSkip):** Video otomatis melompati bagian pembuka (OP) dan penutup (ED) secara otomatis dengan notifikasi visual instan.
                    - **Kunci Layar (Screen Lock):** Tombol kunci di HUD player untuk mencegah sentuhan tidak sengaja saat menonton anime favorit.
                    - **Pilihan Kecepatan Cepat:** Pemilih kecepatan pemutaran instan (1.0x, 1.25x, 1.5x, 2.0x) langsung di panel kontrol video.

                    #### 🎨 Antarmuka Modern & Compact:
                    - **Modern Floating Dock Navigation:** Mengganti bottom navigation bar Material 3 bawaan yang monoton dengan floating dock pill modern, kompak (tinggi 58dp), dan berestetika OLED premium.
                    - **Penanganan Anime Belum Rilis (Upcoming Bugfix):** Memperbaiki bug episode anime Akan Datang yang memunculkan daftar episode kosong/palsu dan menyebabkan HTTP 500. Kini menampilkan kartu status pra-rilis yang informatif.
                    - **Format Catatan Rilis Rapi:** Tampilan changelog kini diformat terstruktur dengan tag pill kategori, bullet point bersih, dan teks tebal yang nyaman dibaca.
                """.trimIndent(),
                publishedAt = "2026-10-27",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.2.0/Miru-v0.2.0-release.apk",
                apkSize = 35900000L,
                isNewer = false
            ),
            AppRelease(
                tagName = "v0.1.6",
                versionName = "0.1.6",
                title = "Tatap Android v0.1.6 (Release Build, Airing Schedule & Report Issue)",
                body = """
                    ### Tatap Android v0.1.6
                    
                    #### 🚀 Build Release Ringan & Mulus:
                    - **Signed Release APK:** Beralih ke build release dengan sertifikat rilis terverifikasi, optimasi compiler penuh, dan penghapusan overhead debug, menghasilkan aplikasi yang jauh lebih ringan dan performa Jetpack Compose mencapai 60–120 FPS mulus.
                    - **ProGuard & R8 Shield:** Proteksi JNI native bridge Rust (libtatap_core.so) dan model serialisasi untuk kestabilan jangka panjang.

                    #### 📅 Kategori Katalog Baru & Jadwal Rilis Mingguan:
                    - **Rilis Hari Ini (Airing Today):** Swimlane anime baru di Beranda yang menampilkan anime yang tayang hari ini lengkap dengan badge waktu tayang (WIB) dan nomor episode terbaru.
                    - **Layar Jadwal Rilis Lengkap (Airing Schedule):** Tampilan jadwal simulcast mingguan dengan selektor hari (Senin - Minggu) dan deteksi otomatis hari ini.
                    - **Kategori Ekstensi Beranda:** Ditambahkan kategori Paling Berperingkat Tinggi (Top Rated), Akan Datang Mendatang (Upcoming), dan Favorit Sepanjang Masa (All-Time Popular).

                    #### 🐛 Sistem Laporkan Masalah (Report Issue):
                    - **Laporkan Masalah / Bug di Pengaturan:** Formulir laporan dengan pengumpulan data diagnostik perangkat otomatis (versi OS, tipe HP, versi Tatap, status daemon core).
                    - **Laporkan Episode Rusak di Player:** Tombol pelaporan langsung di layar error pemutar video yang otomatis menyertakan judul anime, episode, dan pesan error stream.
                    - **Dukungan Kirim ke GitHub & Salin ke Clipboard:** Kemudahan mengirimkan issue terstruktur ke GitHub atau menyalin ringkasan diagnostik untuk dibagikan.
                """.trimIndent(),
                publishedAt = "2026-10-27",
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.1.6/Tatap-v0.1.6-release.apk",
                apkSize = 32000000L,
                isNewer = false
            ),
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
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.1.5/Tatap-v0.1.5-debug.apk",
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
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.1.4/Tatap-v0.1.4-debug.apk",
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
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.1.3/Tatap-v0.1.3-debug.apk",
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
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.1.2/Tatap-v0.1.2-debug.apk",
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
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.1.1/Tatap-v0.1.1-debug.apk",
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
                downloadUrl = "https://github.com/David4rr/miru/releases/download/v0.1.0/Tatap-v0.1.0-debug.apk",
                apkSize = 43700000L,
                isNewer = false
            )
        )
    }
}
