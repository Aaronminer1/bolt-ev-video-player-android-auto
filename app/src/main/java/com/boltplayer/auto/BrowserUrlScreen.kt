package com.boltplayer.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template

/**
 * URL / search entry screen using SearchTemplate (supported since Car App 1.0).
 *
 * - Full URLs (with or without https://) load directly.
 * - Text without '.' is treated as a Google search.
 * - Pops itself on submit, returning to WebBrowserScreen.
 */
class BrowserUrlScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return SearchTemplate.Builder(
            object : SearchTemplate.SearchCallback {
                override fun onSearchTextChanged(searchText: String) {}

                override fun onSearchSubmitted(searchText: String) {
                    val trimmed = searchText.trim()
                    if (trimmed.isNotBlank()) {
                        WebViewManager.loadUrl(trimmed)
                    }
                    screenManager.pop()
                }
            }
        )
            .setShowKeyboardByDefault(true)
            .setSearchHint("Enter URL or search — tap ⌕ to go")
            .build()
    }
}
