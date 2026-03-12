package com.boltplayer.auto

import org.schabi.newpipe.extractor.NewPipe
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class BoltPlayerCarAppService : CarAppService() {

    override fun onCreate() {
        super.onCreate()
        val downloader = NewPipeDownloader.getInstance()
        val cookies = GoogleAuthManager.getCookies(this)
        if (!cookies.isNullOrBlank()) downloader.cookies = cookies
        NewPipe.init(downloader)
    }

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return VideoSession()
    }
}
