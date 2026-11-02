package id.miru.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import id.miru.app.ui.components.player.PlayerPipHelper
import id.miru.app.ui.screens.SplashScreen
import id.miru.app.ui.theme.MiruTheme

class MainActivity : ComponentActivity() {

    private var isPipReceiverRegistered = false

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PlayerPipHelper.ACTION_PIP_PLAY -> {
                    PlayerPipHelper.onPlay?.invoke()
                    PlayerPipHelper.updatePictureInPictureActions(
                        activity = this@MainActivity,
                        isPlaying = true,
                        videoWidth = PlayerPipHelper.videoWidth,
                        videoHeight = PlayerPipHelper.videoHeight
                    )
                }
                PlayerPipHelper.ACTION_PIP_PAUSE -> {
                    PlayerPipHelper.onPause?.invoke()
                    PlayerPipHelper.updatePictureInPictureActions(
                        activity = this@MainActivity,
                        isPlaying = false,
                        videoWidth = PlayerPipHelper.videoWidth,
                        videoHeight = PlayerPipHelper.videoHeight
                    )
                }
                PlayerPipHelper.ACTION_PIP_REWIND -> PlayerPipHelper.onRewind?.invoke()
                PlayerPipHelper.ACTION_PIP_FORWARD -> PlayerPipHelper.onForward?.invoke()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureHighRefreshRate()
        registerPipReceiver()

        setContent {
            MiruTheme {
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) {
                    SplashScreen(onSplashFinished = { showSplash = false })
                } else {
                    MainApp()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiverSafely()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PlayerPipHelper.isPlayerActive && PlayerPipHelper.isPlaying) {
            PlayerPipHelper.enterPictureInPicture(
                activity = this,
                isPlaying = PlayerPipHelper.isPlaying,
                videoWidth = PlayerPipHelper.videoWidth,
                videoHeight = PlayerPipHelper.videoHeight
            )
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerPipHelper.isInPipMode = isInPictureInPictureMode
        PlayerPipHelper.isEnteringPip = false
        PlayerPipHelper.onPipModeChanged?.invoke(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            PlayerPipHelper.updatePictureInPictureActions(
                activity = this,
                isPlaying = PlayerPipHelper.isPlaying,
                videoWidth = PlayerPipHelper.videoWidth,
                videoHeight = PlayerPipHelper.videoHeight
            )
        }
    }

    private fun registerPipReceiver() {
        if (isPipReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(PlayerPipHelper.ACTION_PIP_PLAY)
            addAction(PlayerPipHelper.ACTION_PIP_PAUSE)
            addAction(PlayerPipHelper.ACTION_PIP_REWIND)
            addAction(PlayerPipHelper.ACTION_PIP_FORWARD)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_EXPORTED
        } else {
            0
        }
        ContextCompat.registerReceiver(this, pipReceiver, filter, flags)
        isPipReceiverRegistered = true
    }

    private fun unregisterReceiverSafely() {
        if (!isPipReceiverRegistered) return
        try {
            unregisterReceiver(pipReceiver)
        } catch (_: IllegalArgumentException) {}
        isPipReceiverRegistered = false
    }
    private fun configureHighRefreshRate() {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.supportedModes?.maxByOrNull { it.refreshRate }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.supportedModes?.maxByOrNull { it.refreshRate }
        } else null

        mode?.let { m ->
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = m.modeId
                preferredRefreshRate = m.refreshRate
            }
        }
    }
}
