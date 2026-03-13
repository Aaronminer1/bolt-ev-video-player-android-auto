package com.boltplayer.auto

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.Display
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
 * Car-side screen for phone screen mirroring.
 *
 * Uses NavigationTemplate (the only template with a raw SurfaceContainer) and
 * registers a SurfaceCallback to receive the car display's Surface.  That
 * Surface is handed to MirrorController, which pairs it with the MediaProjection
 * obtained on the phone side to create a VirtualDisplay — live mirroring begins
 * automatically once both are ready.
 *
 * On first start it launches MirrorActivity on the phone to ask for capture
 * permission.  The user approves on the phone; ScreenMirrorService then creates
 * the MediaProjection and notifies MirrorController.
 */
class ScreenMirrorScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    init {
        try {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
            Log.d("BoltMirror", "SurfaceCallback registered")
        } catch (e: Exception) {
            Log.e("BoltMirror", "Failed to register SurfaceCallback: $e")
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Launch the phone-side permission dialog on the phone's display (DEFAULT_DISPLAY=0),
                // NOT on the car display. carContext.startActivity() routes to the car display
                // (launchDisplayId=541) which is denied — so we use the app context directly.
                val appCtx = carContext.applicationContext
                val intent = Intent(appCtx, MirrorActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val opts = ActivityOptions.makeBasic()
                    .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                    .toBundle()
                appCtx.startActivity(intent, opts)
                Log.d("BoltMirror", "Launched MirrorActivity on phone display")
            }

            override fun onDestroy(owner: LifecycleOwner) {
                MirrorController.release()
                carContext.stopService(Intent(carContext, ScreenMirrorService::class.java))
                Log.d("BoltMirror", "ScreenMirrorScreen destroyed, service stopped")
            }
        })
    }

    // ── SurfaceCallback ──────────────────────────────────────────────────────

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        Log.d("BoltMirror", "Car surface available: ${surfaceContainer.width}x${surfaceContainer.height}")
        val density = carContext.resources.displayMetrics.densityDpi
        MirrorController.onCarSurfaceAvailable(
            surface,
            surfaceContainer.width,
            surfaceContainer.height,
            density
        )
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d("BoltMirror", "Car surface destroyed")
        MirrorController.onCarSurfaceDestroyed()
    }

    // ── Template ─────────────────────────────────────────────────────────────

    override fun onGetTemplate(): Template {
        return NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Stop Mirroring")
                            .setOnClickListener {
                                MirrorController.release()
                                carContext.stopService(
                                    Intent(carContext, ScreenMirrorService::class.java)
                                )
                                screenManager.pop()
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
