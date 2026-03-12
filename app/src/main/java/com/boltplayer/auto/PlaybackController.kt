package com.boltplayer.auto

import android.util.Log
import android.view.Surface
import kotlinx.coroutines.CompletableDeferred
import androidx.media3.common.Player
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
    private var surfaceReadySignal: CompletableDeferred<Unit>? = null

    fun setPlayer(exo: ExoPlayer, videoTitle: String, onSurfaceReady: CompletableDeferred<Unit>? = null) {
        release()
        player = exo
        title = videoTitle
        surfaceReadySignal = onSurfaceReady
        // If a surface is already available, set it immediately and signal ready
        _carSurface?.let {
            Log.d("BoltPlayer", "setPlayer: attaching existing carSurface to new player")
            exo.setVideoSurface(it)
            surfaceReadySignal?.complete(Unit)
            surfaceReadySignal = null
        }
    }

    fun setCarSurface(surface: Surface?) {
        _carSurface = surface
        if (surface != null) {
            Log.d("BoltPlayer", "setCarSurface: attaching surface to player=${player != null}")
            player?.setVideoSurface(surface)
            // Signal that the surface is now ready (unblocks YoutubePlayer before prepare())
            surfaceReadySignal?.complete(Unit)
            surfaceReadySignal = null
            // If player was already prepared (surface arrived late), nudge the video renderer
            player?.let { p ->
                if (p.playbackState != Player.STATE_IDLE && p.playbackState != Player.STATE_ENDED) {
                    Log.d("BoltPlayer", "setCarSurface: nudging video renderer at ${p.currentPosition}ms")
                    p.seekTo(p.currentPosition)
                }
            }
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
        surfaceReadySignal = null
    }
}
