package com.boltplayer.auto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service required by Android 14+ to hold a MediaProjection beyond
 * the requesting activity.  Receives the screen-capture result intent from
 * MirrorActivity, creates the MediaProjection, and hands it to MirrorController.
 *
 * Must declare android:foregroundServiceType="mediaProjection" in the manifest.
 */
class ScreenMirrorService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "bolt_mirror"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bolt Player — Mirroring")
            .setContentText("Screen mirroring active. DRM apps (Netflix, etc.) will appear black due to Android content protection.")
            .setSmallIcon(android.R.drawable.ic_menu_slideshow)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }

        if (resultCode == 0 || data == null) {
            Log.e("BoltMirror", "Service started without valid projection result")
            stopSelf()
            return START_NOT_STICKY
        }

        val mgr = getSystemService(MediaProjectionManager::class.java)
        val projection = mgr.getMediaProjection(resultCode, data)
        MirrorController.onProjectionGranted(projection)

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("BoltMirror", "ScreenMirrorService destroyed")
        MirrorController.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Mirror",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
