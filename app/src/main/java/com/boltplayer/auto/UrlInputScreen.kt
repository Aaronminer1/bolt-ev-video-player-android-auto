package com.boltplayer.auto

import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.media3.common.MimeTypes

class UrlInputScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}

            override fun onSearchSubmitted(searchText: String) {
                val url = searchText.trim()
                if (url.isNotBlank()) {
                    val mimeType = when {
                        url.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
                        url.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
                        else -> null
                    }
                    val exo = androidx.media3.exoplayer.ExoPlayer
                        .Builder(carContext.applicationContext).build()
                    val mediaItem = if (mimeType != null) {
                        androidx.media3.common.MediaItem.Builder()
                            .setUri(Uri.parse(url)).setMimeType(mimeType).build()
                    } else {
                        androidx.media3.common.MediaItem.fromUri(Uri.parse(url))
                    }
                    exo.setMediaItem(mediaItem)
                    exo.prepare()
                    exo.play()
                    PlaybackController.setPlayer(exo, "Stream")
                    screenManager.push(VideoPlayerScreen(carContext, "Stream"))
                }
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Paste URL: m3u8, mpd, mp4, etc.")
            .setShowKeyboardByDefault(true)
            .build()
    }
}
