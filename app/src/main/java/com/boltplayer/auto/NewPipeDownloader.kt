package com.boltplayer.auto

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request as OkHttpRequest
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

class NewPipeDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    @Volatile var cookies: String = ""

    companion object {
        @Volatile private var instance: NewPipeDownloader? = null

        fun getInstance(): NewPipeDownloader =
            instance ?: synchronized(this) {
                instance ?: NewPipeDownloader(OkHttpClient()).also { instance = it }
            }
    }

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val data = request.dataToSend()

        val body = when {
            method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true) -> null
            data != null -> data.toRequestBody()
            else -> ByteArray(0).toRequestBody()
        }

        val req = OkHttpRequest.Builder()
            .url(request.url())
            .method(method, body)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
            .apply {
                val c = cookies
                if (c.isNotBlank()) {
                    header("Cookie", c)
                } else {
                    android.util.Log.w("BoltPlayer", "NewPipeDownloader: NO COOKIES for ${request.url().take(60)}")
                }
                request.headers().forEach { (name, values) ->
                    values.forEach { addHeader(name, it) }
                }
            }
            .build()

        val resp = client.newCall(req).execute()

        if (resp.code == 429) throw ReCaptchaException("Rate limited by YouTube", request.url())

        val headers = mutableMapOf<String, MutableList<String>>()
        resp.headers.forEach { (n, v) -> headers.getOrPut(n) { mutableListOf() }.add(v) }

        return Response(
            resp.code,
            resp.message,
            headers,
            resp.body?.string() ?: "",
            resp.request.url.toString()
        )
    }
}
