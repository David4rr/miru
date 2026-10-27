package id.miru.app

import android.app.Application
import android.util.Log

class MiruApp : Application() {
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

    override fun onTerminate() {
        super.onTerminate()
        NativeBridge.stopDaemon()
    }
}
