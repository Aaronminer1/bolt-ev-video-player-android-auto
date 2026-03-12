package com.boltplayer.auto

import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * "Now Playing" screen rendered on the car head unit using NavigationTemplate.
 *
 * NavigationTemplate is the only Car App Library template that exposes a
 * SurfaceContainer — a hardware Surface that the app can draw into.
 * We attach the car-side ExoPlayer's video output to this Surface so actual
 * video frames appear on the head unit display.
 *
 * Zero invalidate() calls — state changes go directly through PlaybackController.
 */
class VideoPlayerScreen(
    carContext: CarContext,
    private val videoTitle: String
) : Screen(carContext), SurfaceCallback {

    init {
        // Register for surface callbacks so we get the car display's Surface.
        // This requires ACCESS_SURFACE + NAVIGATION_TEMPLATES permissions (already in manifest).
        try {
            carContext.getCarService(AppManager::class.java)
                .setSurfaceCallback(this)
            Log.d("BoltPlayer", "VideoPlayerScreen: SurfaceCallback registered")
        } catch (e: Exception) {
            Log.e("BoltPlayer", "VideoPlayerScreen: failed to register SurfaceCallback: $e")
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                Log.d("BoltPlayer", "VideoPlayerScreen: onDestroy, releasing player")
                PlaybackController.setCarSurface(null)
                PlaybackController.release()
            }
        })
    }

    // ── SurfaceCallback ──────────────────────────────────────────────────────

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.d("BoltPlayer", "onSurfaceAvailable: ${surfaceContainer.width}x${surfaceContainer.height}")
        PlaybackController.setCarSurface(surfaceContainer.surface)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d("BoltPlayer", "onSurfaceDestroyed")
        PlaybackController.setCarSurface(null)
    }

    // ── Template ─────────────────────────────────────────────────────────────

    override fun onGetTemplate(): Template {
        val isPlaying = PlaybackController.player?.isPlaying ?: false

        // Play/Pause — dynamic icon reflects current state
        val playPauseAction = Action.Builder()
            .setIcon(CarIcon.Builder(
                IconCompat.createWithResource(
                    carContext,
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
            ).build())
            .setTitle(if (isPlaying) "Pause" else "Play")
            .setOnClickListener {
                PlaybackController.player?.let { p ->
                    if (p.isPlaying) p.pause() else p.play()
                }
                invalidate() // refresh icon
            }
            .build()

        // Skip back 15 seconds
        val skipBackAction = Action.Builder()
            .setIcon(CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_skip_back)
            ).build())
            .setTitle("-15s")
            .setOnClickListener {
                PlaybackController.player?.let { p ->
                    p.seekTo((p.currentPosition - 15_000L).coerceAtLeast(0L))
                }
            }
            .build()

        // Skip forward 30 seconds
        val skipForwardAction = Action.Builder()
            .setIcon(CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_skip_forward)
            ).build())
            .setTitle("+30s")
            .setOnClickListener {
                PlaybackController.player?.let { p ->
                    p.seekTo((p.currentPosition + 30_000L).coerceAtMost(
                        p.duration.coerceAtLeast(0L)))
                }
            }
            .build()

        // Restart — seek to beginning and resume
        val restartAction = Action.Builder()
            .setIcon(CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_restart)
            ).build())
            .setOnClickListener {
                PlaybackController.player?.let { p ->
                    p.seekTo(0L)
                    p.play()
                }
            }
            .build()

        // Stop — halt playback and return to results
        val stopAction = Action.Builder()
            .setIcon(CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_stop)
            ).build())
            .setOnClickListener {
                PlaybackController.player?.stop()
                screenManager.pop()
            }
            .build()

        return NavigationTemplate.Builder()
            // Side strip: back | skip-back | play/pause | skip-forward
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Back")
                            .setOnClickListener { screenManager.pop() }
                            .build()
                    )
                    .addAction(skipBackAction)
                    .addAction(playPauseAction)
                    .addAction(skipForwardAction)
                    .build()
            )
            // Floating overlay strip on the video surface: restart | stop
            .setMapActionStrip(
                ActionStrip.Builder()
                    .addAction(restartAction)
                    .addAction(stopAction)
                    .build()
            )
            .build()
    }
}

