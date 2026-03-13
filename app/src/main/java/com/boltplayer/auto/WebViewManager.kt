package com.boltplayer.auto

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Singleton that renders a WebView to the car's Surface using a VirtualDisplay +
 * Presentation.
 *
 * The car's Surface is backed by a VirtualDisplay. A Presentation (Dialog subclass)
 * is shown on that virtual display, with the WebView filling it. Hardware acceleration
 * works natively — no draw(canvas) hacks, no SYSTEM_ALERT_WINDOW permission needed.
 *
 * All public methods must be called from the main thread.
 */
object WebViewManager {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: WebPresentation? = null
    private var webView: WebView? = null

    // Holds the URL to load once the next init() completes (surface was torn down
    // while a navigation was requested, e.g. user typed URL then the screen popped).
    internal var pendingUrl: String? = null

    /** Set by WebBrowserScreen to be notified when a page input field is focused. */
    var onInputFocused: ((currentValue: String) -> Unit)? = null

    /** Called when the injected nav bar's URL button is tapped. */
    var onUrlRequested: (() -> Unit)? = null

    /** Called when the injected nav bar's Exit button is tapped. */
    var onExitRequested: (() -> Unit)? = null

    /** Current page URL — updated on every onPageStarted. */
    var currentUrl: String = "https://www.google.com"

    /** Called on the main thread whenever the browser navigates to a new URL. */
    var onUrlChanged: ((String) -> Unit)? = null

    fun init(context: Context, surface: Surface, width: Int, height: Int, density: Int) {
        release()
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val vd = dm.createVirtualDisplay(
                "BoltBrowser",
                width, height, density,
                surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            )
            virtualDisplay = vd

            val startUrl = pendingUrl ?: "https://www.google.com"
            pendingUrl = null
            val pres = WebPresentation(context.applicationContext, vd.display, startUrl)
            pres.show()
            webView = pres.webView
            presentation = pres
            Log.d("BoltBrowser", "VirtualDisplay + Presentation created ${width}x${height} dpi=$density url=$startUrl")
        } catch (e: Exception) {
            Log.e("BoltBrowser", "init failed: $e")
        }
    }

    fun loadUrl(rawUrl: String) {
        val url = when {
            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
            rawUrl.contains(".") -> "https://$rawUrl"
            else -> "https://www.google.com/search?q=${Uri.encode(rawUrl)}"
        }
        Log.d("BoltBrowser", "loadUrl: $url (webView=${webView != null})")
        val wv = webView
        if (wv != null) {
            wv.loadUrl(url)
        } else {
            // Surface is currently torn down (a child screen is visible); queue for next init()
            pendingUrl = url
        }
    }

    fun goBack() { webView?.goBack() }
    fun reload() { webView?.reload() }

    fun scroll(distanceX: Float, distanceY: Float) {
        webView?.scrollBy(distanceX.toInt(), distanceY.toInt())
    }

    fun click(x: Float, y: Float) {
        val wv = webView ?: return
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 100L, MotionEvent.ACTION_UP, x, y, 0)
        wv.dispatchTouchEvent(down)
        wv.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    /**
     * Called by FormInputScreen when the user submits text from the car keyboard.
     * Injects the value into the currently focused input/textarea in the WebView
     * and simulates pressing Enter so search boxes and forms activate.
     */
    fun submitInput(value: String) {
        val wv = webView ?: return
        val escaped = value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
        val js = """
            (function() {
                var el = document.activeElement;
                if (!el || (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA')) {
                    el = document.querySelector('input:not([type=hidden]),textarea');
                }
                if (el) {
                    el.value = '$escaped';
                    el.dispatchEvent(new Event('input',  {bubbles:true}));
                    el.dispatchEvent(new Event('change', {bubbles:true}));
                    el.dispatchEvent(new KeyboardEvent('keydown',  {key:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));
                    el.dispatchEvent(new KeyboardEvent('keypress', {key:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));
                    el.dispatchEvent(new KeyboardEvent('keyup',    {key:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));
                    if (el.form) el.form.submit();
                }
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    fun release() {
        presentation?.dismiss()
        presentation = null
        webView = null
        virtualDisplay?.release()
        virtualDisplay = null
        // Don't clear pendingUrl here — it may have been set just before release
        Log.d("BoltBrowser", "WebViewManager released")
    }
}

/**
 * A Dialog shown on a VirtualDisplay — gives the WebView a proper hardware-accelerated
 * window that renders directly to the VirtualDisplay (and therefore to the car Surface).
 */
class WebPresentation(
    context: Context,
    display: Display,
    private val startUrl: String = "https://www.google.com"
) : Presentation(context, display) {

    var webView: WebView? = null
        private set

    private inner class JsBridge {
        @JavascriptInterface
        fun onInputFocus(currentValue: String) {
            Handler(Looper.getMainLooper()).post {
                WebViewManager.onInputFocused?.invoke(currentValue)
            }
        }

        @JavascriptInterface
        fun goBack() {
            Handler(Looper.getMainLooper()).post { WebViewManager.goBack() }
        }

        @JavascriptInterface
        fun reloadPage() {
            Handler(Looper.getMainLooper()).post { WebViewManager.reload() }
        }

        @JavascriptInterface
        fun openUrlDialog() {
            Handler(Looper.getMainLooper()).post { WebViewManager.onUrlRequested?.invoke() }
        }

        @JavascriptInterface
        fun exitBrowser() {
            Handler(Looper.getMainLooper()).post { WebViewManager.onExitRequested?.invoke() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wv = WebView(context)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            setSupportZoom(true)
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            allowFileAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // Desktop Chrome UA — YouTube (and many other sites) specifically block
            // WebView/mobile UA strings but allow desktop Chrome through without restrictions.
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
        }
        // Enable cookies (including third-party) — required for YouTube playback & login
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }
        wv.addJavascriptInterface(JsBridge(), "Android")
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                val scheme = url.scheme ?: ""
                // Intercept non-http(s) schemes (youtube://, intent://, vnd.youtube://, etc.)
                if (scheme != "http" && scheme != "https" && scheme != "javascript" && scheme != "about") {
                    val videoId = url.getQueryParameter("v")
                        ?: url.getQueryParameter("video_id")
                        ?: run {
                            val raw = url.toString()
                            val idx = raw.indexOf("v=")
                            if (idx != -1) raw.substring(idx + 2).substringBefore("&").substringBefore("#") else null
                        }
                    if (videoId != null) {
                        view.loadUrl(youtubeEmbedUrl(videoId))
                    }
                    return true
                }
                // Redirect youtube.com/watch URLs to the embed player.
                // Android WebView sends X-Requested-With: <package> which YouTube's server
                // detects and blocks. The embed player is designed for WebView/iframes
                // and skips those checks.
                val host = url.host ?: ""
                if ((host == "www.youtube.com" || host == "youtube.com" || host == "m.youtube.com")
                    && url.path?.startsWith("/watch") == true) {
                    val videoId = url.getQueryParameter("v")
                    if (videoId != null) {
                        view.loadUrl(youtubeEmbedUrl(videoId))
                        return true
                    }
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Track current URL and notify WebBrowserScreen so it can refresh the ActionStrip
                WebViewManager.currentUrl = url
                Handler(Looper.getMainLooper()).post { WebViewManager.onUrlChanged?.invoke(url) }
                // Inject early — before page JS runs — to spoof a real Chrome browser.
                view.evaluateJavascript(CHROME_SPOOF_JS, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // Re-inject Chrome spoof in case the page replaced window.chrome during load
                view.evaluateJavascript(CHROME_SPOOF_JS, null)
                // Inject input-focus listener + bottom nav bar
                view.evaluateJavascript(PAGE_INJECT_JS, null)
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            private var fullscreenView: View? = null
            private var fullscreenCallback: CustomViewCallback? = null

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                // Video requested fullscreen — swap the WebView out for the fullscreen view
                fullscreenView = view
                fullscreenCallback = callback
                val container = FrameLayout(context).apply {
                    addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                this@WebPresentation.setContentView(container)
            }

            override fun onHideCustomView() {
                fullscreenCallback?.onCustomViewHidden()
                fullscreenView = null
                fullscreenCallback = null
                // Restore the WebView
                webView?.let { this@WebPresentation.setContentView(it) }
            }
        }
        wv.loadUrl(startUrl)
        webView = wv
        setContentView(wv)
    }

    companion object {
        fun youtubeEmbedUrl(videoId: String) =
            "https://www.youtube.com/embed/$videoId?autoplay=1&fs=1&rel=0&playsinline=0"

        /** Injected every page load — installs the input-focus listener for the car keyboard. */
        val PAGE_INJECT_JS = """
            (function() {
                if (!window.__boltInputListenerInstalled) {
                    window.__boltInputListenerInstalled = true;
                    document.addEventListener('focusin', function(e) {
                        var t = e.target;
                        if (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA') {
                            try { Android.onInputFocus(t.value || ''); } catch(err) {}
                        }
                    });
                }
            })();
        """.trimIndent()

        /**
         * Injected at onPageStarted + onPageFinished to make the WebView look like
         * a real desktop Chrome browser. YouTube checks all of these in JavaScript.
         */
        val CHROME_SPOOF_JS = """
            (function() {
                if (window.__boltChromeSpoof) return;
                window.__boltChromeSpoof = true;

                // 1. Override navigator.userAgent in JS (HTTP header already spoofed via settings)
                try {
                    Object.defineProperty(navigator, 'userAgent', {
                        get: function() {
                            return 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';
                        }, configurable: true
                    });
                } catch(e) {}

                // 2. Hide webdriver flag (signals automation/non-human browser)
                try {
                    Object.defineProperty(navigator, 'webdriver', {
                        get: function() { return false; }, configurable: true
                    });
                } catch(e) {}

                // 3. Create window.chrome — real Chrome has this; its absence is a clear
                //    WebView signal that YouTube and others check for.
                if (!window.chrome) {
                    window.chrome = {
                        runtime: {},
                        loadTimes: function() { return {}; },
                        csi: function() {
                            return { startE: Date.now(), onloadT: Date.now(), pageT: Date.now(), tran: 15 };
                        },
                        app: { isInstalled: false, InstallState: { DISABLED: 'disabled', INSTALLED: 'installed', NOT_INSTALLED: 'not_installed' }, RunningState: { CANNOT_RUN: 'cannot_run', READY_TO_RUN: 'ready_to_run', RUNNING: 'running' } }
                    };
                }

                // 4. Fix plugins array — empty in WebView, populated in Chrome
                try {
                    Object.defineProperty(navigator, 'plugins', {
                        get: function() {
                            return [{ name: 'Chrome PDF Plugin' }, { name: 'Chrome PDF Viewer' }, { name: 'Native Client' }];
                        }, configurable: true
                    });
                } catch(e) {}
            })();
        """.trimIndent()
    }
}
