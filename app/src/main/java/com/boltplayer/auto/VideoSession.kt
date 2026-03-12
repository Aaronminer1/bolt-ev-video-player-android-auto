package com.boltplayer.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class VideoSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return FileBrowserScreen(carContext)
    }
}
