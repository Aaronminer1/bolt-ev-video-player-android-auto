package com.boltplayer.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import java.security.MessageDigest
import java.util.UUID

/**
 * Shared helper — extracts streams for a YouTube watch URL and starts playback
 * on the car ExoPlayer. Used by both YouTubeResultsScreen and YouTubeLibraryScreen.
 */
object YoutubePlayer {

    /** Custom DRM callback that posts to YouTube's signed Widevine license URL. */
    private class YouTubeDrmCallback(
        private val licenseUrl: String,
        private val headers: Map<String, String>
    ) : MediaDrmCallback {
        private val http = OkHttpClient.Builder().build()

        override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): ByteArray {
            val url = request.defaultUrl + "&signedRequest=" + String(request.data, Charsets.US_ASCII)
            return http.newCall(Request.Builder().url(url).build()).execute()
                .use { it.body?.bytes() ?: ByteArray(0) }
        }

        override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): ByteArray {
            val reqBuilder = Request.Builder()
                .url(licenseUrl)
                .post(request.data.toRequestBody("application/octet-stream".toMediaType()))
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            return http.newCall(reqBuilder.build()).execute().use { response ->
                val code = response.code
                val bytes = response.body?.bytes() ?: ByteArray(0)
                Log.d("BoltPlayer", "DRM key response: HTTP $code size=${bytes.size}")
                if (code != 200) {
                    Log.e("BoltPlayer", "DRM error body: ${String(bytes).take(300)}")
                    return@use bytes
                }
                // YouTube's legacy /api/drm/ endpoint returns GLS format:
                //   GLS/1.0 0 OK\r\n[headers]\r\n\r\n[binary Widevine license protobuf]
                // The Widevine CDM needs only the binary part after the \r\n\r\n separator.
                val crlf2 = bytes.indexOfCrlfCrlf()
                if (crlf2 >= 0 && bytes.size > 3 &&
                    bytes[0] == 'G'.code.toByte() && bytes[1] == 'L'.code.toByte()) {
                    val license = bytes.copyOfRange(crlf2 + 4, bytes.size)
                    Log.d("BoltPlayer", "GLS format stripped: ${crlf2 + 4} header bytes, ${license.size} license bytes")
                    license
                } else {
                    bytes
                }
            }
        }
    }

    /** Finds the index of \r\n\r\n in a byte array, returns -1 if not found. */
    private fun ByteArray.indexOfCrlfCrlf(): Int {
        for (i in 0..size - 4) {
            if (this[i] == 0x0d.toByte() && this[i+1] == 0x0a.toByte() &&
                this[i+2] == 0x0d.toByte() && this[i+3] == 0x0a.toByte()) return i
        }
        return -1
    }

    private fun computeSapisidHash(cookies: String): String {
        // Try __Secure-3PAPISID first, then SAPISID
        val sapisid = cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("__Secure-3PAPISID=") || it.startsWith("SAPISID=") }
            ?.substringAfter("=")?.trim()
            ?: return ""
        val timestamp = System.currentTimeMillis() / 1000
        val message = "$timestamp $sapisid https://www.youtube.com"
        val digest = MessageDigest.getInstance("SHA-1").digest(message.toByteArray())
        val hash = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_${hash}"
    }

    /**
     * Fallback stream resolver using YouTube InnerTube /player API with full auth headers.
     * Used when NewPipe fails due to age-restriction or login requirements.
     */
    private fun fetchStreamUrlViaInnerTube(videoId: String, cookies: String, userAgent: String): String {
        val sapisid = cookies.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("__Secure-3PAPISID=") || it.startsWith("SAPISID=") }
            ?.substringAfter("=")?.trim() ?: ""
        val timestamp = System.currentTimeMillis() / 1000
        val sapisidHash = if (sapisid.isNotBlank()) {
            val digest = MessageDigest.getInstance("SHA-1")
                .digest("$timestamp $sapisid https://www.youtube.com".toByteArray())
                .joinToString("") { "%02x".format(it) }
            "SAPISIDHASH ${timestamp}_${digest}"
        } else ""

        val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20260312.06.00","platform":"DESKTOP","hl":"en","gl":"US"}},"videoId":"$videoId","racyCheckOk":true,"contentCheckOk":true}"""

        val client = OkHttpClient.Builder().build()
        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", userAgent)
            .header("Content-Type", "application/json")
            .header("X-YouTube-Client-Name", "1")
            .header("X-YouTube-Client-Version", "2.20260312.06.00")
            .header("Origin", "https://www.youtube.com")
            .apply {
                if (cookies.isNotBlank()) header("Cookie", cookies)
                if (sapisidHash.isNotBlank()) header("Authorization", sapisidHash)
            }
            .build()

        val responseBody = client.newCall(req).execute().use { it.body?.string() ?: "" }
        val json = org.json.JSONObject(responseBody)
        Log.d("BoltPlayer", "InnerTube player status: ${json.optJSONObject("playabilityStatus")?.optString("status")}")

        val formats = json.optJSONObject("streamingData")
        val dashUrl = formats?.optString("dashManifestUrl")?.takeIf { it.isNotBlank() }
        val hlsUrl = formats?.optString("hlsManifestUrl")?.takeIf { it.isNotBlank() }
        val muxedUrl = formats?.optJSONArray("formats")
            ?.let { arr -> (0 until arr.length()).asSequence().map { arr.getJSONObject(it) } }
            ?.filter { it.has("url") }
            ?.maxByOrNull { it.optInt("height", 0) }
            ?.optString("url")?.takeIf { it.isNotBlank() }

        return hlsUrl ?: dashUrl ?: muxedUrl
            ?: throw Exception("No playable stream in InnerTube player response")
    }

    fun play(
        carContext: CarContext,
        screen: Screen,
        videoUrl: String,
        videoTitle: String,
        scope: CoroutineScope
    ) {
        CarToast.makeText(carContext, "Loading: ${videoTitle.take(40)}", CarToast.LENGTH_SHORT).show()
        scope.launch {
            try {
                Log.d("BoltPlayer", "YoutubePlayer.play: $videoUrl")
                // Ensure NewPipe downloader always has up-to-date cookies
                val latestCookies = GoogleAuthManager.getCookies(carContext) ?: ""
                NewPipeDownloader.getInstance().cookies = latestCookies
                Log.d("BoltPlayer", "NewPipe cookies set: ${if (latestCookies.isBlank()) "EMPTY!" else "${latestCookies.length} chars, starts=${latestCookies.take(30)}"}")
                val streamUrl = withContext(Dispatchers.IO) {
                    val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                    val extractor = service.getStreamExtractor(videoUrl)
                    var attempt = 0
                    try {
                        while (true) {
                            try { extractor.fetchPage(); break } catch (e: Exception) {
                                if (attempt++ < 3 && e.message?.contains("reloaded", ignoreCase = true) == true) {
                                    Thread.sleep(500)
                                } else throw e
                            }
                        }
                        val hlsUrl  = try { extractor.hlsUrl.takeIf { it.isNotBlank() } } catch (_: Exception) { null }
                        val dashUrl = try { extractor.dashMpdUrl.takeIf { it.isNotBlank() } } catch (_: Exception) { null }
                        val bestMuxed = try {
                            @Suppress("DEPRECATION")
                            extractor.videoStreams.filter { !it.isVideoOnly }
                                .maxByOrNull { it.height }?.content?.takeIf { it.isNotBlank() }
                        } catch (_: Exception) { null }
                        hlsUrl ?: dashUrl ?: bestMuxed
                            ?: extractor.videoStreams.maxByOrNull { it.height }?.content
                            ?: throw Exception("No playable stream found")
                    } catch (e: AgeRestrictedContentException) {
                        // NewPipe can't bypass age restriction — fall back to InnerTube player API with full auth
                        Log.w("BoltPlayer", "NewPipe age-restricted, trying InnerTube fallback for $videoUrl")
                        val videoId = videoUrl.substringAfter("v=").substringBefore("&")
                        fetchStreamUrlViaInnerTube(videoId, latestCookies, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    }
                }
                Log.d("BoltPlayer", "Stream URL type=${streamUrl.take(50)}")

                val cookies = GoogleAuthManager.getCookies(carContext) ?: ""
                val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

                // Determine MIME type so ExoPlayer picks the right parser
                val mimeType = when {
                    streamUrl.contains("/manifest/hls") || streamUrl.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
                    streamUrl.contains("/manifest/dash") || streamUrl.contains(".mpd") -> MimeTypes.APPLICATION_MPD
                    else -> null
                }

                // For DASH streams, YouTube embeds the Widevine license URL as yt:laUrl in the manifest.
                // ExoPlayer doesn't parse this YouTube-specific tag, so we fetch and extract it manually.
                val licenseUrl = if (mimeType == MimeTypes.APPLICATION_MPD) {
                    withContext(Dispatchers.IO) {
                        try {
                            val client = OkHttpClient.Builder().build()
                            val req = okhttp3.Request.Builder()
                                .url(streamUrl)
                                .header("User-Agent", userAgent)
                                .apply { if (cookies.isNotBlank()) header("Cookie", cookies) }
                                .build()
                            val body = client.newCall(req).execute().use { it.body?.string() ?: "" }
                            // YouTube embeds license URL as <yt:SystemURL type="widevine">URL</yt:SystemURL>
                            // The URL contains signed params (expire, sig, etc.) — we must use this exact URL
                            val laUrl = Regex("""<yt:SystemURL type="widevine">(.*?)</yt:SystemURL>""", RegexOption.DOT_MATCHES_ALL)
                                .find(body)?.groupValues?.get(1)
                                ?.replace("&amp;", "&")?.trim()
                            if (laUrl != null) {
                                Log.d("BoltPlayer", "Widevine license URL: ${laUrl.take(100)}")
                            } else {
                                Log.w("BoltPlayer", "No yt:SystemURL widevine in manifest, using fallback")
                                // Log a snippet for debugging
                                val snippet = body.indexOf("ContentProtection").let { i ->
                                    if (i >= 0) body.substring(i, minOf(i + 800, body.length)) else ""
                                }
                                if (snippet.isNotBlank()) Log.d("BoltPlayer", "CP snippet: $snippet")
                            }
                            laUrl ?: "https://www.youtube.com/api/license/widevine"
                        } catch (e: Exception) {
                            Log.w("BoltPlayer", "Failed to fetch manifest: $e")
                            "https://www.youtube.com/api/license/widevine"
                        }
                    }
                } else null

                // For DRM-protected streams (DASH/HLS), include YouTube cookies in the Widevine license request
                val sapisidHash = computeSapisidHash(cookies)
                Log.d("BoltPlayer", "SAPISIDHASH computed: ${sapisidHash.take(30)}...")
                val drmHeaders = if (cookies.isNotBlank() && mimeType != null) {
                    buildMap {
                        put("Cookie", cookies)
                        put("User-Agent", userAgent)
                        put("Origin", "https://www.youtube.com")
                        put("Referer", videoUrl)
                        put("X-YouTube-Client-Name", "1")
                        put("X-YouTube-Client-Version", "2.20260312.06.00")
                        if (sapisidHash.isNotBlank()) put("Authorization", sapisidHash)
                    }
                } else emptyMap()

                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .apply { if (mimeType != null) setMimeType(mimeType) }
                    .build() // DRM is handled via custom session manager below

                val okHttpClient = OkHttpClient.Builder().build()
                val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                    .setDefaultRequestProperties(mapOf(
                        "User-Agent" to userAgent,
                        "Cookie" to cookies
                    ))

                // Use custom DRM callback so we can log exactly what YouTube returns
                val drmCallback = YouTubeDrmCallback(
                    licenseUrl ?: "https://www.youtube.com/api/license/widevine",
                    drmHeaders
                )
                // Use default L1 provider. Widevine L1 HD requires hardware-secure output which
                // Android Auto's surface doesn't support. We cap video to 480p SD below so
                // YouTube's license server issues SW_SECURE_DECODE keys instead of HW_SECURE_ALL.
                val drmSessionManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(drmCallback)

                val mediaSourceFactory = DefaultMediaSourceFactory(carContext.applicationContext)
                    .setDataSourceFactory(dataSourceFactory)
                    .setDrmSessionManagerProvider { drmSessionManager }

                // Cap video to 480p: YouTube's Widevine license for SD streams uses
                // SW_SECURE_DECODE (no hardware-secure output required) vs HW_SECURE_ALL
                // for HD. This lets the video render on Android Auto's non-secure surface.
                val trackSelector = DefaultTrackSelector(carContext.applicationContext).apply {
                    setParameters(
                        buildUponParameters()
                            .setMaxVideoSize(854, 480)
                            .setMaxVideoBitrate(2_500_000)
                            .build()
                    )
                }

                val exo = ExoPlayer.Builder(carContext.applicationContext)
                    .setTrackSelector(trackSelector)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build()
                exo.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true
                )
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("BoltPlayer", "ExoPlayer error: code=${error.errorCode} msg=${error.message} cause=${error.cause?.javaClass?.simpleName}: ${error.cause?.message?.take(120)}")
                        CarToast.makeText(carContext, "Playback error: ${error.message?.take(60)}", CarToast.LENGTH_LONG).show()
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        val stateName = when(state){1->"IDLE";2->"BUFFERING";3->"READY";4->"ENDED";else->"?"}
                        Log.d("BoltPlayer", "ExoPlayer state: $stateName")
                        if (state == Player.STATE_READY) {
                            // Log selected tracks so we can see which video/audio streams are active
                            val tracks = exo.currentTracks
                            tracks.groups.forEach { group ->
                                for (i in 0 until group.length) {
                                    if (group.isTrackSelected(i)) {
                                        val fmt = group.getTrackFormat(i)
                                        Log.d("BoltPlayer", "Selected track: type=${group.type} mime=${fmt.sampleMimeType} w=${fmt.width} h=${fmt.height} ch=${fmt.channelCount}")
                                    }
                                }
                            }
                        }
                    }
                })
                exo.setMediaItem(mediaItem)

                // Push the player screen first so the Car framework starts creating
                // the surface immediately. Then wait for it before calling prepare(),
                // so DRM MediaCodec is configured with the output surface from the
                // start — calling setOutputSurface() mid-stream on a secure codec
                // is silently rejected on Samsung/hardware-DRM devices.
                val surfaceReady = CompletableDeferred<Unit>()
                PlaybackController.setPlayer(exo, videoTitle, onSurfaceReady = surfaceReady)
                screen.screenManager.push(VideoPlayerScreen(carContext, videoTitle))

                // Wait up to 3 seconds for the surface — then prepare regardless
                val gotSurface = withTimeoutOrNull(3000L) { surfaceReady.await() } != null
                Log.d("BoltPlayer", "Surface ready before prepare: $gotSurface")

                exo.prepare()
                exo.play()

            } catch (e: PaidContentException) {
                Log.w("BoltPlayer", "Paid content: $videoUrl")
                CarToast.makeText(carContext, "Open YouTube on your phone to watch purchased content", CarToast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("BoltPlayer", "YoutubePlayer.play failed: $e")
                CarToast.makeText(carContext, "Error: ${e.message?.take(80) ?: "unknown"}", CarToast.LENGTH_LONG).show()
            }
        }
    }
}
