package com.boltplayer.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template

/**
 * Car-side screen shown whenever a WebView input/textarea is focused.
 * Uses SearchTemplate (the only car-side template with a real keyboard) to
 * collect text, then injects it back into the focused element via JavaScript.
 */
class FormInputScreen(
    carContext: CarContext,
    private val currentValue: String
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return SearchTemplate.Builder(
            object : SearchTemplate.SearchCallback {
                override fun onSearchTextChanged(searchText: String) {}

                override fun onSearchSubmitted(searchText: String) {
                    val trimmed = searchText.trim()
                    if (looksLikeUrl(trimmed)) {
                        // User typed a URL — navigate rather than filling the page field
                        WebViewManager.loadUrl(trimmed)
                    } else {
                        WebViewManager.submitInput(trimmed)
                    }
                    screenManager.pop()
                }
            }
        )
            .setInitialSearchText(currentValue)
            .setShowKeyboardByDefault(true)
            .setSearchHint("Type to fill field…")
            .build()
    }

    private fun looksLikeUrl(text: String): Boolean {
        if (text.isBlank() || text.contains(" ")) return false
        if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("www.")) return true
        // domain-like: at least one dot with chars on both sides, e.g. "youtube.com"
        val dotIndex = text.indexOf('.')
        return dotIndex > 0 && dotIndex < text.length - 1
    }
}
