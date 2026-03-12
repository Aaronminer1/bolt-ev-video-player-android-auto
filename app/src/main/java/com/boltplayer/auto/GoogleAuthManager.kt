package com.boltplayer.auto

import android.content.Context

/**
 * Stores YouTube session cookies captured from the WebView login.
 * No Google Cloud Console registration needed.
 */
object GoogleAuthManager {

    private const val PREFS_NAME = "bolt_auth"
    private const val KEY_COOKIES = "yt_cookies"
    private const val KEY_EMAIL  = "yt_email"

    fun saveCookies(context: Context, cookies: String, email: String = "YouTube Account") {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COOKIES, cookies)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun getCookies(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COOKIES, null)

    fun getAccountEmail(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null)

    fun isSignedIn(context: Context): Boolean = getCookies(context) != null

    fun signOut(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
