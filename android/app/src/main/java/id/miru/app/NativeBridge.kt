package id.miru.app

import android.util.Log

object NativeBridge {
    private const val TAG = "NativeBridge"

    init {
        try {
            System.loadLibrary("miru_core")
            Log.i(TAG, "Successfully loaded libmiru_core.so")
        } catch (e: UnsatisfiedLinkError) {
            try {
                System.loadLibrary("tatap_core")
                Log.i(TAG, "Successfully loaded fallback libtatap_core.so")
            } catch (e2: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libmiru_core.so: ${e.message}; fallback failed: ${e2.message}")
            }
        }
    }

    external fun startDaemon(dataDir: String, port: Int): Boolean
    external fun stopDaemon(): Boolean
    external fun isDaemonRunning(): Boolean
}
