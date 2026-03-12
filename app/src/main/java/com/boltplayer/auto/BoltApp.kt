package com.boltplayer.auto

import android.app.Application
import android.content.Context
import android.content.Intent

/**
 * Application subclass that installs a global crash handler.
 * Any uncaught exception is saved to SharedPreferences and then
 * CrashActivity is launched to show the full stack trace on-screen,
 * so we never have to hunt through logcat to find a bug.
 */
class BoltApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Write crash to prefs so CrashActivity can read it.
                val msg = buildString {
                    appendLine("Thread: ${thread.name}")
                    appendLine("Error: ${throwable}")
                    appendLine()
                    appendLine(throwable.stackTraceToString())
                }
                getSharedPreferences("crash", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", msg)
                    .commit()   // commit() not apply() — process is dying

                // Launch CrashActivity from a fresh task.
                val intent = Intent(this, CrashActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                }
                startActivity(intent)
            } catch (_: Exception) {
                // If our handler crashes, fall back to the system handler.
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
