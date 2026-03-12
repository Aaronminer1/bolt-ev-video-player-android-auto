package com.boltplayer.auto

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/**
 * Phone-side Activity that shows a WebView for YouTube sign-in.
 *
 * No Google Cloud Console registration is needed — after the user logs in
 * via the normal YouTube web sign-in page, the session cookies are captured
 * from the WebView's CookieManager and saved locally.
 *
 * Those cookies are later used to download the user's subscription list via
 * YouTube's OPML export endpoint (no API key required).
 */
class GoogleSignInActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (GoogleAuthManager.isSignedIn(this)) {
            // Already signed in — show a small confirmation, then close
            val email = GoogleAuthManager.getAccountEmail(this)
            Toast.makeText(this, "Already signed in as $email\n\nTo switch accounts, sign out from the car app first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled  = true
            settings.domStorageEnabled  = true
            settings.userAgentString    =
                "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                Log.d("BoltPlayer", "WebView page: $url")

                // Once we're on any youtube.com page, check if session cookies
                // are present — that's proof the sign-in succeeded.
                // We don't restrict on path (/signin, etc.) because Google
                // redirects through several intermediate URLs before landing.
                if (url.contains("youtube.com") &&
                    !url.contains("accounts.google.com")) {

                    CookieManager.getInstance().flush()   // ensure cookies are written to disk

                    // Collect cookies from both www and m subdomains and merge unique ones
                    val wwwCookies = CookieManager.getInstance().getCookie("https://www.youtube.com") ?: ""
                    val mCookies   = CookieManager.getInstance().getCookie("https://m.youtube.com") ?: ""
                    val cookies = (wwwCookies.split(";") + mCookies.split(";"))
                        .map { it.trim() }.filter { it.isNotEmpty() }
                        .distinctBy { it.substringBefore("=").trim() }
                        .joinToString("; ")

                    Log.d("BoltPlayer", "YT cookies on $url: ${cookies.take(120)}")

                    // Any of these cookies indicate an active session
                    if (cookies.contains("SAPISID") || cookies.contains("SID=") ||
                        cookies.contains("__Secure-3PSID") || cookies.contains("APISID") ||
                        cookies.contains("HSID") || cookies.contains("SSID")) {
                        // Try to extract email via JS — falls back to "YouTube Account"
                        view.evaluateJavascript(
                            "(function(){ try { return document.querySelector('yt-formatted-string#account-name')?.textContent?.trim() || ''; } catch(e){ return ''; } })()"
                        ) { result ->
                            val email = result?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
                                ?: "YouTube Account"
                            GoogleAuthManager.saveCookies(this@GoogleSignInActivity, cookies, email)
                            Log.d("BoltPlayer", "YouTube cookies saved, email=$email")
                            runOnUiThread {
                                Toast.makeText(
                                    this@GoogleSignInActivity,
                                    "Signed in as $email\nOpen the car app to see My Subscriptions.",
                                    Toast.LENGTH_LONG
                                ).show()
                                finish()
                            }
                        }
                    }
                }
            }
        }

        // Load YouTube's sign-in page
        webView.loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&hl=en&continue=https://www.youtube.com")
        setContentView(webView)
    }
}
