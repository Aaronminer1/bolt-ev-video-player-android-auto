package com.boltplayer.auto

import org.schabi.newpipe.extractor.NewPipe
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BoltPlayerCarAppService : CarAppService() {

    override fun onCreate() {
        super.onCreate()
        // Init off main thread so the service binds quickly to the car head unit
        CoroutineScope(Dispatchers.IO).launch {
            val downloader = NewPipeDownloader.getInstance()
            val cookies = GoogleAuthManager.getCookies(this@BoltPlayerCarAppService)
            if (!cookies.isNullOrBlank()) downloader.cookies = cookies
            NewPipe.init(downloader)
        }
    }

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return VideoSession()
    }
}
