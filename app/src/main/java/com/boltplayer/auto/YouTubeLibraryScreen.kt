package com.boltplayer.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Fetches a YouTube feed URL (Watch History, Watch Later, Purchases, etc.)
 * using session cookies, parses the embedded ytInitialData JSON, and shows
 * the resulting videos in a scrollable list.
 *
 * No API key required — uses the same cookies captured at sign-in.
 */
class YouTubeLibraryScreen(
    carContext: CarContext,
    private val title: String,
    private val feedUrl: String
) : Screen(carContext) {

    data class VideoItem(val id: String, val title: String, val channel: String)

    private val scope = MainScope()
    private var videos: List<VideoItem> = emptyList()
    private var isLoading = true
    private var errorMessage: String? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) { fetchFeed() }
            override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
        })
    }

    private fun fetchFeed() {
        val cookies = GoogleAuthManager.getCookies(carContext) ?: run {
            errorMessage = "Please sign in first — tap Sign In from the Streaming menu"
            isLoading = false
            invalidate()
            return
        }
        scope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()
                    val req = Request.Builder()
                        .url(feedUrl)
                        .header("Cookie", cookies)
                        .header("User-Agent",
                            "Mozilla/5.0 (Linux; Android 14; Pixel 7) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Referer", "https://m.youtube.com/")
                        .build()

                    val resp = client.newCall(req).execute()
                    val finalUrl = resp.request.url.toString()
                    val body = resp.body?.string() ?: ""

                    Log.d("BoltPlayer", "Library response: code=${resp.code} finalUrl=$finalUrl bodyLen=${body.length}")
                    Log.d("BoltPlayer", "Body snippet: ${body.take(300)}")

                    // If we ended up on accounts.google.com, cookies aren't working
                    if (finalUrl.contains("accounts.google.com") || finalUrl.contains("google.com/signin")) {
                        throw Exception("Cookies invalid — please sign out and sign in again")
                    }
                    if (body.length < 500) throw Exception("Empty response (code=${resp.code})")

                    parseYtInitialData(body)
                }
                videos = items
                isLoading = false
            } catch (e: Exception) {
                Log.e("BoltPlayer", "Library fetch failed: $e")
                errorMessage = e.message?.take(100) ?: "Failed to load"
                isLoading = false
            }
            invalidate()
        }
    }

    /**
     * YouTube embeds all page data as:  var ytInitialData = {...};
     * We extract that JSON blob and walk the renderer tree to find videoRenderer nodes.
     */
    private fun parseYtInitialData(html: String): List<VideoItem> {
        val startMarker = "var ytInitialData = "
        val start = html.indexOf(startMarker)
        if (start == -1) {
            Log.w("BoltPlayer", "ytInitialData not found in page")
            return emptyList()
        }
        // Find the matching closing brace for the JSON object
        var depth = 0
        var end = start + startMarker.length
        for (i in (start + startMarker.length) until html.length) {
            when (html[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { end = i + 1; break } }
            }
        }
        val rawJson = html.substring(start + startMarker.length, end)
        // YouTube mobile pages encode the JSON using JS \xNN hex escapes — decode them first
        val jsonStr = rawJson.replace(Regex("""\\x([0-9a-fA-F]{2})""")) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
        val root = try { JSONObject(jsonStr) } catch (e: Exception) {
            Log.e("BoltPlayer", "ytInitialData parse error: $e")
            return emptyList()
        }

        val results = mutableListOf<VideoItem>()
        collectVideoRenderers(root, results)
        Log.d("BoltPlayer", "Parsed ${results.size} videos from $feedUrl")
        return results
    }

    /** Recursively walks the JSON tree collecting all videoRenderer nodes. */
    private fun collectVideoRenderers(obj: Any, out: MutableList<VideoItem>) {
        when (obj) {
            is JSONObject -> {
                if (obj.has("videoRenderer")) {
                    parseVideoRenderer(obj.getJSONObject("videoRenderer"))?.let { out.add(it) }
                }
                if (obj.has("playlistVideoRenderer")) {
                    parseVideoRenderer(obj.getJSONObject("playlistVideoRenderer"))?.let { out.add(it) }
                }
                obj.keys().forEach { key -> collectVideoRenderers(obj.get(key), out) }
            }
            is JSONArray -> {
                for (i in 0 until obj.length()) collectVideoRenderers(obj.get(i), out)
            }
        }
    }

    private fun parseVideoRenderer(vr: JSONObject): VideoItem? {
        return try {
            val videoId = vr.optString("videoId").takeIf { it.isNotBlank() } ?: return null
            val titleText = vr.optJSONObject("title")
                ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: vr.optJSONObject("title")?.optString("simpleText")
                ?: return null
            val channel = vr.optJSONObject("ownerText")
                ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: vr.optJSONObject("shortBylineText")
                    ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: ""
            VideoItem(videoId, titleText, channel)
        } catch (e: Exception) { null }
    }

    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)

        if (isLoading) return builder.setLoading(true).build()

        val listBuilder = ItemList.Builder()
        when {
            errorMessage != null -> listBuilder.setNoItemsMessage(errorMessage!!)
            videos.isEmpty()     -> listBuilder.setNoItemsMessage("No videos found")
            else -> videos.forEach { v ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(v.title)
                        .addText(v.channel)
                        .setImage(CarIcon.Builder(
                            IconCompat.createWithResource(carContext, R.drawable.ic_video)
                        ).build())
                        .setOnClickListener {
                            val url = "https://www.youtube.com/watch?v=${v.id}"
                            YoutubePlayer.play(carContext, this@YouTubeLibraryScreen, url, v.title, scope)
                        }
                        .build()
                )
            }
        }
        return builder.setSingleList(listBuilder.build()).build()
    }
}
