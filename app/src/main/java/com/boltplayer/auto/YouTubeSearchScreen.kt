package com.boltplayer.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template

class YouTubeSearchScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}

            override fun onSearchSubmitted(searchText: String) {
                if (searchText.isNotBlank()) {
                    screenManager.push(YouTubeResultsScreen(carContext, searchText.trim()))
                }
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Search YouTube…")
            .setShowKeyboardByDefault(true)
            .build()
    }
}
