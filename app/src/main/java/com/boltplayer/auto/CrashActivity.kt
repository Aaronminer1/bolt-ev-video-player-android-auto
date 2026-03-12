package com.boltplayer.auto

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Full-screen activity shown after a crash.
 * Reads the saved stack trace from SharedPreferences and displays
 * it so you can see exactly what went wrong without needing logcat.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crash = getSharedPreferences("crash", Context.MODE_PRIVATE)
            .getString("last_crash", "No crash details saved.") ?: "No crash details saved."

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
        }

        val title = TextView(this).apply {
            text = "Bolt Player crashed"
            textSize = 20f
            setTextColor(0xFFCC0000.toInt())
            setPadding(0, 0, 0, 16)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val details = TextView(this).apply {
            text = crash
            textSize = 11f
            setTextIsSelectable(true)   // user can copy the text
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
        }
        scroll.addView(details)

        val copyBtn = Button(this).apply {
            text = "Copy to clipboard"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", crash))
                text = "Copied!"
            }
        }

        val restartBtn = Button(this).apply {
            text = "Restart app"
            setOnClickListener {
                getSharedPreferences("crash", Context.MODE_PRIVATE).edit().clear().apply()
                startActivity(Intent(this@CrashActivity, PermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
            }
        }

        root.addView(title)
        root.addView(scroll)
        root.addView(copyBtn)
        root.addView(restartBtn)
        setContentView(root)
    }
}
