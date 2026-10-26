package id.tatap.app

import android.util.Log

object NativeBridge {
    private const val TAG = "NativeBridge"

    init {
        try {
            System.loadLibrary("tatap_core")
            Log.i(TAG, "Successfully loaded libtatap_core.so")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libtatap_core.so: ${e.message}")
        }
    }

    external fun startDaemon(dataDir: String, port: Int): Boolean
    external fun stopDaemon(): Boolean
    external fun isDaemonRunning(): Boolean
}
