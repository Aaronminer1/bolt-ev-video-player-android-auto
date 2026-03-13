package com.boltplayer.auto

import android.Manifest
import android.app.AlertDialog
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
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionActivity : Activity() {

    private lateinit var statusText: TextView

    companion object {
        private const val REQUEST_STORAGE = 1001
        private const val PREFS_DISCLAIMER = "bolt_disclaimer"
        private const val KEY_ACCEPTED = "disclaimer_accepted_v1"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val accepted = getSharedPreferences(PREFS_DISCLAIMER, MODE_PRIVATE)
            .getBoolean(KEY_ACCEPTED, false)

        if (!accepted) {
            showDisclaimerDialog()
        } else {
            buildMainUI()
        }
    }

    private fun showDisclaimerDialog() {
        val scrollView = ScrollView(this)
        val textView = TextView(this).apply {
            text = """
⚠️ SAFETY WARNING & DISCLAIMER

DO NOT USE WHILE DRIVING.

This application is intended for use ONLY when:
• The vehicle is legally and safely PARKED, or
• You are a passenger and not operating the vehicle

Watching video while driving is ILLEGAL in most jurisdictions and EXTREMELY DANGEROUS. It significantly increases the risk of accidents, injury, and death.

EXPERIMENTAL SOFTWARE
This is unofficial, experimental software not affiliated with Google, YouTube, or General Motors. It is provided "AS IS" with NO WARRANTY of any kind. It may crash or stop working at any time.

NO LIABILITY
The authors bear NO responsibility for any injury, accident, property damage, legal consequence, or loss of any kind arising from use of this software. By tapping "I Understand & Accept" you acknowledge that you:

1. Will only use this app when safely parked
2. Accept all risks associated with its use
3. Release the authors from all liability
4. Have read and agree to the full disclaimer at:
github.com/Aaronminer1/bolt-ev-video-player-android-auto/blob/main/DISCLAIMER.md

Licensed under GPL-3.0. Source code available at the GitHub link above.
            """.trimIndent()
            setPadding(40, 32, 40, 32)
            textSize = 14f
            setLineSpacing(4f, 1f)
        }
        scrollView.addView(textView)

        AlertDialog.Builder(this)
            .setTitle("Before You Continue")
            .setView(scrollView)
            .setCancelable(false)
            .setPositiveButton("I Understand & Accept") { _, _ ->
                getSharedPreferences(PREFS_DISCLAIMER, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_ACCEPTED, true)
                    .apply()
                buildMainUI()
            }
            .setNegativeButton("Decline & Exit") { _, _ ->
                finish()
            }
            .show()
    }

    private fun buildMainUI() {
        val layout = LinearLayout(this).apply {            orientation = LinearLayout.VERTICAL
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
        if (::statusText.isInitialized) updateStatus()
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
