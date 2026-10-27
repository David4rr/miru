package id.miru.app

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class MiruApp : Application(), ImageLoaderFactory {
    companion object {
        private const val TAG = "MiruApp"
        const val DAEMON_PORT = 8767
    }

    override fun onCreate() {
        super.onCreate()
        val dataDir = filesDir.absolutePath
        val started = NativeBridge.startDaemon(dataDir, DAEMON_PORT)
        Log.i(TAG, "Miru Rust Core daemon launch status: $started on port $DAEMON_PORT")
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("miru_image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTerminate() {
        super.onTerminate()
        NativeBridge.stopDaemon()
    }
}
