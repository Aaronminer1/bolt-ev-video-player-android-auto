package com.boltplayer.auto

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Car-side screen that renders a full WebView via VirtualDisplay + Presentation.
 *
 * The car Surface is passed to WebViewManager which creates a VirtualDisplay backed
 * by that surface, then shows a Presentation (Dialog) on the virtual display's Display.
 * The WebView inside the Presentation renders via hardware acceleration directly to
 * the VirtualDisplay → car surface. No draw(canvas) copying, no render loop needed.
 *
 * Scroll/click events from the car are forwarded to the WebView as MotionEvents.
 * No SYSTEM_ALERT_WINDOW permission required.
 */
class WebBrowserScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    init {
        try {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        } catch (e: Exception) {}

        // When a page input field is focused, push FormInputScreen so the user
        // can type via the car's built-in keyboard (SearchTemplate).
        WebViewManager.onInputFocused = { currentValue ->
            screenManager.push(FormInputScreen(carContext, currentValue))
        }
        WebViewManager.onUrlRequested = {
            screenManager.push(BrowserUrlScreen(carContext))
        }
        WebViewManager.onExitRequested = {
            screenManager.pop()
        }
        // Refresh the ActionStrip URL button whenever the browser navigates
        WebViewManager.onUrlChanged = { invalidate() }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                WebViewManager.onInputFocused = null
                WebViewManager.onUrlRequested = null
                WebViewManager.onExitRequested = null
                WebViewManager.onUrlChanged = null
                WebViewManager.pendingUrl = null
                WebViewManager.release()
            }
        })
    }

    // ── SurfaceCallback ──────────────────────────────────────────────────────

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        WebViewManager.init(
            carContext,
            surface,
            surfaceContainer.width,
            surfaceContainer.height,
            surfaceContainer.dpi
        )
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        WebViewManager.release()
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        WebViewManager.scroll(distanceX, distanceY)
    }

    override fun onClick(x: Float, y: Float) {
        WebViewManager.click(x, y)
    }

    // ── Template ─────────────────────────────────────────────────────────────

    override fun onGetTemplate(): Template {
        // Build a Chrome-style top navigation bar using ActionStrip:
        //   ◀ Back  |  ↻ Reload  |  [current domain]  |  ✕ Exit
        val displayUrl = try {
            android.net.Uri.parse(WebViewManager.currentUrl)
                .host
                ?.removePrefix("www.")
                ?.take(24)
                ?: "Go to URL"
        } catch (e: Exception) { "Go to URL" }

        val backAction = Action.Builder()
            .setTitle("\u25C4 Back")
            .setOnClickListener { WebViewManager.goBack() }
            .build()
        val reloadAction = Action.Builder()
            .setTitle("\u21BB Reload")
            .setOnClickListener { WebViewManager.reload() }
            .build()
        val urlAction = Action.Builder()
            .setTitle(displayUrl)
            .setOnClickListener { screenManager.push(BrowserUrlScreen(carContext)) }
            .build()
        val exitAction = Action.Builder()
            .setTitle("\u2715 Exit")
            .setOnClickListener { screenManager.pop() }
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(backAction)
                    .addAction(reloadAction)
                    .addAction(urlAction)
                    .addAction(exitAction)
                    .build()
            )
            .build()
    }
}
