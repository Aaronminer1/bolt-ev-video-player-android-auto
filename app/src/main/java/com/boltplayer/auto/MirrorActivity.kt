package com.boltplayer.auto

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle

/**
 * Transparent phone-side activity that shows the system "Allow screen capture?"
 * dialog.  On approval it starts ScreenMirrorService (which holds the
 * MediaProjection token and creates the VirtualDisplay).  Then finishes
 * immediately so the user sees the car screen.
 */
class MirrorActivity : Activity() {

    companion object {
        private const val REQUEST_CAPTURE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mgr = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenMirrorService::class.java).apply {
                putExtra(ScreenMirrorService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenMirrorService.EXTRA_DATA, data)
            }
            startForegroundService(serviceIntent)
        }
        finish()
    }
}
