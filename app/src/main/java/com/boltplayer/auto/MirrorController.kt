package com.boltplayer.auto

import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface

/**
 * Singleton that coordinates the phone-side MediaProjection with the car-side
 * SurfaceContainer.  Either can arrive first; once both are present the
 * VirtualDisplay is created and mirroring begins.
 *
 * All VirtualDisplay operations are dispatched on a dedicated background
 * HandlerThread so that car app SurfaceCallback calls return immediately and
 * never trigger the 5-second Car App ANR timeout.
 */
object MirrorController {

    private val bgThread = HandlerThread("BoltMirrorBg").also { it.start() }
    private val bgHandler = Handler(bgThread.looper)

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null

    @Volatile private var pendingSurface: Surface? = null
    @Volatile private var pendingWidth = 0
    @Volatile private var pendingHeight = 0
    @Volatile private var pendingDensity = 0

    fun onProjectionGranted(projection: MediaProjection) {
        Log.d("BoltMirror", "Projection granted")
        bgHandler.post {
            mediaProjection = projection
            val surface = pendingSurface
            if (surface != null && pendingWidth > 0) {
                startVirtualDisplay(surface, pendingWidth, pendingHeight, pendingDensity)
            }
        }
    }

    fun onCarSurfaceAvailable(surface: Surface, width: Int, height: Int, density: Int) {
        Log.d("BoltMirror", "Car surface available: ${width}x${height} dpi=$density")
        // Store immediately (main thread safe — volatile fields)
        pendingSurface = surface
        pendingWidth = width
        pendingHeight = height
        pendingDensity = density
        // Start VirtualDisplay on background thread so onSurfaceAvailable returns fast
        bgHandler.post {
            if (mediaProjection != null) {
                startVirtualDisplay(surface, width, height, density)
            }
        }
    }

    fun onCarSurfaceDestroyed() {
        Log.d("BoltMirror", "Car surface destroyed")
        pendingSurface = null
        bgHandler.post {
            virtualDisplay?.release()
            virtualDisplay = null
        }
    }

    private fun startVirtualDisplay(surface: Surface, width: Int, height: Int, density: Int) {
        virtualDisplay?.release()
        val mp = mediaProjection ?: return
        Log.d("BoltMirror", "Creating VirtualDisplay ${width}x${height}")
        // Android 14+ requires a callback to be registered before createVirtualDisplay()
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d("BoltMirror", "MediaProjection stopped")
                bgHandler.post {
                    virtualDisplay?.release()
                    virtualDisplay = null
                    mediaProjection = null
                }
            }
        }, bgHandler)
        virtualDisplay = mp.createVirtualDisplay(
            "BoltMirror",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, bgHandler
        )
        Log.d("BoltMirror", "VirtualDisplay created: $virtualDisplay")
    }

    fun release() {
        Log.d("BoltMirror", "Releasing mirror")
        bgHandler.post {
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection?.stop()
            mediaProjection = null
        }
        pendingSurface = null
        pendingWidth = 0
        pendingHeight = 0
    }
}

