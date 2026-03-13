package com.boltplayer.auto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionActivity : Activity() {

    private lateinit var statusText: TextView

    companion object {
        private const val REQUEST_STORAGE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val titleView = TextView(this).apply {
            text = "Bolt Player"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        val grantButton = Button(this).apply {
            text = "Grant Storage Permission"
            setOnClickListener { requestStoragePermission() }
        }

        val overlayButton = Button(this).apply {
            text = "Grant Browser Permission (Overlay)"
            setOnClickListener { requestOverlayPermission() }
        }

        val youtubeButton = Button(this).apply {
            text = "YouTube Search"
            setOnClickListener {
                startActivity(android.content.Intent(this@PermissionActivity, PhoneYouTubeActivity::class.java))
            }
        }

        val infoText = TextView(this).apply {
            text = "1. Tap \"Grant Storage Permission\" above.\n" +
                   "2. Put video files on your phone.\n" +
                   "3. Connect phone to your Bolt.\n" +
                   "4. Open Bolt Player from the car display."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }

        layout.addView(titleView)
        layout.addView(statusText)
        layout.addView(grantButton)
        layout.addView(overlayButton)
        layout.addView(youtubeButton)
        layout.addView(infoText)

        setContentView(layout)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_STORAGE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE) {
            updateStatus()
        }
    }

    private fun updateStatus() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val granted = ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED

        statusText.text = if (granted) {
            "✓ Storage permission granted"
        } else {
            "Storage permission needed to browse videos"
        }
    }
}
