package com.boltplayer.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * "Movies & TV" section.  Signed-in users see their account library links at the top;
 * everyone gets quick-search shortcuts for free YouTube content.
 */
class MoviesBrowseScreen(carContext: CarContext) : Screen(carContext) {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { invalidate() }
        })
    }

    override fun onGetTemplate(): Template {
        val isSignedIn = GoogleAuthManager.isSignedIn(carContext)
        val listBuilder = ItemList.Builder()

        // ── Account library ──────────────────────────────────────────────────
        listBuilder.addItem(libraryRow(
            "My Purchases / Rentals",
            if (isSignedIn) "Movies & shows you've bought or rented" else "Sign in to view your purchases",
            "https://m.youtube.com/paid_memberships?ybp=ggECIAE%3D"
        ))
        if (isSignedIn) {
            listBuilder.addItem(libraryRow(
                "Watch Later",
                "Videos saved to your Watch Later list",
                "https://m.youtube.com/playlist?list=WL"
            ))
            listBuilder.addItem(libraryRow(
                "Watch History",
                "Recently watched videos",
                "https://m.youtube.com/feed/history"
            ))
        }

        // ── Free browse categories ───────────────────────────────────────────
        listBuilder.addItem(searchRow("Free Movies",        "free full movie english"))
        listBuilder.addItem(searchRow("New Releases",       "new movie 2025 2026 full"))
        listBuilder.addItem(searchRow("Action Movies",      "action movie full free"))
        listBuilder.addItem(searchRow("Comedy Movies",      "comedy movie full free"))
        listBuilder.addItem(searchRow("Horror Movies",      "horror movie full free"))
        listBuilder.addItem(searchRow("Documentaries",      "full documentary 2025"))
        listBuilder.addItem(searchRow("TV Show Episodes",   "full episode tv show free"))

        return ListTemplate.Builder()
            .setTitle("Movies & TV")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }

    private fun libraryRow(title: String, subtitle: String, url: String): Row =
        Row.Builder()
            .setTitle(title)
            .addText(subtitle)
            .setImage(CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_video)
            ).build())
            .setOnClickListener {
                screenManager.push(YouTubeLibraryScreen(carContext, title, url))
            }
            .build()

    private fun searchRow(title: String, query: String): Row =
        Row.Builder()
            .setTitle(title)
            .setImage(CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_play)
            ).build())
            .setOnClickListener {
                screenManager.push(YouTubeResultsScreen(carContext, query))
            }
            .build()
}
