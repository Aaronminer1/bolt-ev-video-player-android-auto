package com.boltplayer.auto

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request as OkHttpRequest
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

class NewPipeDownloader private constructor(private val client: OkHttpClient) : Downloader() {

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
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            .apply {
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
