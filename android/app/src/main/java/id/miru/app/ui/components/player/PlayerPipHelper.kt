package id.miru.app.ui.components.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object PlayerPipHelper {
    const val ACTION_PIP_PLAY = "id.miru.app.pip.PLAY"
    const val ACTION_PIP_PAUSE = "id.miru.app.pip.PAUSE"
    const val ACTION_PIP_REWIND = "id.miru.app.pip.REWIND"
    const val ACTION_PIP_FORWARD = "id.miru.app.pip.FORWARD"

    var isPlayerActive by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var videoWidth by mutableIntStateOf(16)
    var videoHeight by mutableIntStateOf(9)
    var isInPipMode by mutableStateOf(false)
    var isEnteringPip by mutableStateOf(false)
    var onPipModeChanged: ((Boolean) -> Unit)? = null
    var onPlay: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onRewind: (() -> Unit)? = null
    var onForward: (() -> Unit)? = null

    fun isPipSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun calculatePipRational(width: Int, height: Int): Rational {
        if (width <= 0 || height <= 0) return Rational(16, 9)
        val ratio = width.toFloat() / height.toFloat()
        return when {
            ratio < 0.42f -> Rational(42, 100)
            ratio > 2.38f -> Rational(238, 100)
            else -> Rational(width, height)
        }
    }

    private fun createRemoteAction(
        context: Context,
        iconRes: Int,
        title: String,
        requestCode: Int,
        action: String
    ): RemoteAction? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val intent = Intent(action).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context.applicationContext, requestCode, intent, flags)
        val icon = Icon.createWithResource(context, iconRes)
        return RemoteAction(icon, title, title, pendingIntent)
    }

    fun buildPipActions(context: Context, isPlaying: Boolean): List<RemoteAction> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        val rew = createRemoteAction(context, android.R.drawable.ic_media_rew, "Mundur 10d", 101, ACTION_PIP_REWIND)
        val playPause = if (isPlaying) {
            createRemoteAction(context, android.R.drawable.ic_media_pause, "Jeda", 102, ACTION_PIP_PAUSE)
        } else {
            createRemoteAction(context, android.R.drawable.ic_media_play, "Putar", 103, ACTION_PIP_PLAY)
        }
        val ff = createRemoteAction(context, android.R.drawable.ic_media_ff, "Maju 10d", 104, ACTION_PIP_FORWARD)
        return listOfNotNull(rew, playPause, ff)
    }

    fun buildPipParams(
        context: Context,
        isPlaying: Boolean,
        videoWidth: Int = 16,
        videoHeight: Int = 9
    ): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val rational = calculatePipRational(videoWidth, videoHeight)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(rational)
            .setActions(buildPipActions(context, isPlaying))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(isPlayerActive && isPlaying)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    fun enterPictureInPicture(
        activity: Activity,
        isPlaying: Boolean,
        videoWidth: Int = 16,
        videoHeight: Int = 9
    ): Boolean {
        if (!isPipSupported(activity)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = buildPipParams(activity, isPlaying, videoWidth, videoHeight) ?: return false
            isEnteringPip = true
            return try {
                activity.enterPictureInPictureMode(params)
            } catch (_: Exception) {
                isEnteringPip = false
                false
            }
        }
        return false
    }

    fun updatePictureInPictureActions(
        activity: Activity,
        isPlaying: Boolean,
        videoWidth: Int = 16,
        videoHeight: Int = 9
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = buildPipParams(activity, isPlaying, videoWidth, videoHeight) ?: return
            try {
                activity.setPictureInPictureParams(params)
            } catch (_: Exception) {}
        }
    }

    fun resetPictureInPictureParams(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val builder = PictureInPictureParams.Builder()
                    .setActions(emptyList())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(false)
                }
                activity.setPictureInPictureParams(builder.build())
            } catch (_: Exception) {}
        }
    }
}
