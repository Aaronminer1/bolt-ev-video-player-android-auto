package com.boltplayer.auto

import android.util.Log
import android.view.Surface
import androidx.media3.exoplayer.ExoPlayer

/**
 * Process-wide singleton holding the active ExoPlayer and the car display Surface.
 *
 * Both the Car App Service and phone Activities run in the same process so
 * this object is shared without IPC.
 *
 * Video routing:
 *  - carSurface  → set by VideoPlayerScreen via AppManager.setSurfaceCallback()
 *  - player      → car-side ExoPlayer that renders video to carSurface
 *  - When both are available, setVideoSurface() is called automatically.
 */
object PlaybackController {
    var player: ExoPlayer? = null
    var title: String = ""
    private var _carSurface: Surface? = null

    fun setPlayer(exo: ExoPlayer, videoTitle: String) {
        release()
        player = exo
        title = videoTitle
        // Attach to car surface immediately if one is already available
        _carSurface?.let {
            Log.d("BoltPlayer", "setPlayer: attaching existing carSurface to new player")
            exo.setVideoSurface(it)
        }
    }

    fun setCarSurface(surface: Surface?) {
        _carSurface = surface
        if (surface != null) {
            Log.d("BoltPlayer", "setCarSurface: attaching surface to player=${player != null}")
            player?.setVideoSurface(surface)
        } else {
            Log.d("BoltPlayer", "setCarSurface: surface destroyed, clearing video surface")
            player?.clearVideoSurface()
        }
    }

    fun release() {
        player?.clearVideoSurface()
        player?.release()
        player = null
        title = ""
    }
}
