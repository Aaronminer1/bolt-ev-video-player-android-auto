package com.boltplayer.auto

import android.content.Intent
import android.view.Display
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class StreamingScreen(carContext: CarContext) : Screen(carContext) {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { invalidate() }
        })
    }

    override fun onGetTemplate(): Template {
        val isSignedIn = GoogleAuthManager.isSignedIn(carContext)
        val email = GoogleAuthManager.getAccountEmail(carContext)
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("YouTube")
                .addText("Search and stream YouTube videos")
                .setImage(CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_video)
                ).build())
                .setOnClickListener { screenManager.push(YouTubeSearchScreen(carContext)) }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Movies & TV")
                .addText("Browse movies, TV shows, and your account library")
                .setImage(CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_video)
                ).build())
                .setOnClickListener { screenManager.push(MoviesBrowseScreen(carContext)) }
                .build()
        )

        if (isSignedIn) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("My Subscriptions")
                    .addText(email ?: "")
                    .setImage(CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.ic_video)
                    ).build())
                    .setOnClickListener { screenManager.push(SubscriptionsScreen(carContext)) }
                    .build()
            )
        }

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Stream URL")
                .addText("Play any HLS, DASH, or direct video URL (IPTV, etc.)")
                .setImage(CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_play)
                ).build())
                .setOnClickListener { screenManager.push(UrlInputScreen(carContext)) }
                .build()
        )

        if (isSignedIn) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Sign Out")
                    .addText(email ?: "")
                    .setOnClickListener {
                        GoogleAuthManager.signOut(carContext)
                        invalidate()
                    }
                    .build()
            )
        } else {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Sign In to YouTube")
                    .addText("Access your subscriptions — check your phone after tapping")
                    .setOnClickListener {
                        try {
                            val intent = Intent(carContext, GoogleSignInActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            val options = android.app.ActivityOptions.makeBasic().apply {
                                launchDisplayId = Display.DEFAULT_DISPLAY
                            }
                            carContext.applicationContext.startActivity(intent, options.toBundle())
                        } catch (e: Exception) {
                            android.util.Log.e("BoltPlayer", "Sign in launch failed: $e")
                        }
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("Streaming")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
