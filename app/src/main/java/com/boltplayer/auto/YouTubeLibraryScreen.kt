package com.boltplayer.auto

import android.util.Log
import androidx.car.app.CarContext
import java.security.MessageDigest
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fetches a YouTube library feed using the InnerTube API (no HTML scraping).
 * Uses session cookies captured at sign-in — no API key required.
 */
class YouTubeLibraryScreen(
    carContext: CarContext,
    private val title: String,
    private val feedUrl: String
) : Screen(carContext) {

    data class VideoItem(
        val id: String,
        val title: String,
        val channel: String,
        val views: String = "",
        val uploadDate: String = "",
        val description: String = ""
    )

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

    private fun feedUrlToBrowseId(): Pair<String, String?> = when {
        feedUrl.contains("feed/history")     -> Pair("FEhistory", null)
        feedUrl.contains("list=WL")          -> Pair("VLWL", null)
        feedUrl.contains("paid_memberships") ||
        feedUrl.contains("feed/purchases")   -> Pair("FEmemberships_and_purchases", "kgMFggECIAE%3D")
        else                                 -> throw IllegalArgumentException("Unknown feed: $feedUrl")
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
                    val (browseId, params) = feedUrlToBrowseId()

                    val paramsJson = if (params != null) ""","params":"$params"""" else ""
                    val bodyJson = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20260312.06.00","hl":"en","gl":"US"}},"browseId":"$browseId"$paramsJson}"""

                    val client = OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()

                    // Compute SAPISIDHASH for Authorization header
                    val sapisid = cookies.split(";")
                        .firstOrNull { it.trim().startsWith("SAPISID=") }
                        ?.substringAfter("SAPISID=")?.trim() ?: ""
                    val timestamp = System.currentTimeMillis() / 1000
                    val sapisidHash = if (sapisid.isNotBlank()) {
                        val digest = MessageDigest.getInstance("SHA-1")
                            .digest("$timestamp $sapisid https://www.youtube.com".toByteArray())
                        digest.joinToString("") { "%02x".format(it) }
                    } else ""
                    Log.d("BoltPlayer", "SAPISIDHASH sapisid=${sapisid.take(8)}.. ts=$timestamp hash=${sapisidHash.take(12)}..")

                    val req = Request.Builder()
                        .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                        .post(bodyJson.toRequestBody("application/json".toMediaType()))
                        .header("Cookie", cookies)
                        .header("Authorization", "SAPISIDHASH ${timestamp}_${sapisidHash}")
                        .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                        .header("X-YouTube-Client-Name", "1")
                        .header("X-YouTube-Client-Version", "2.20260312.06.00")
                        .header("Origin", "https://www.youtube.com")
                        .header("X-Origin", "https://www.youtube.com")
                        .header("Referer", "https://www.youtube.com/")
                        .header("Accept", "application/json")
                        .build()

                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string() ?: ""

                    Log.d("BoltPlayer", "InnerTube browseId=$browseId code=${resp.code} len=${body.length}")
                    Log.d("BoltPlayer", "InnerTube snippet: ${body.take(200)}")

                    if (!resp.isSuccessful) throw Exception("Server error ${resp.code}")
                    if (body.length < 50) throw Exception("Empty response")

                    val root = JSONObject(body)
                    val results = mutableListOf<VideoItem>()
                    collectRenderers(root, results)

                    // Follow continuation tokens to load all pages (max 6 extra pages / 300 items)
                    var contToken = extractContinuationToken(root)
                    var page = 0
                    while (contToken != null && results.size < 300 && page < 6) {
                        val contBody = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20260312.06.00","hl":"en","gl":"US"}},"continuation":"$contToken"}"""
                        val contReq = Request.Builder()
                            .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                            .post(contBody.toRequestBody("application/json".toMediaType()))
                            .header("Cookie", cookies)
                            .header("Authorization", "SAPISIDHASH ${timestamp}_${sapisidHash}")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                            .header("X-YouTube-Client-Name", "1")
                            .header("X-YouTube-Client-Version", "2.20260312.06.00")
                            .header("Origin", "https://www.youtube.com")
                            .header("X-Origin", "https://www.youtube.com")
                            .header("Referer", "https://www.youtube.com/")
                            .header("Accept", "application/json")
                            .build()
                        val contResp = client.newCall(contReq).execute()
                        val contRaw = contResp.body?.string() ?: break
                        if (!contResp.isSuccessful || contRaw.length < 50) break
                        val contRoot = JSONObject(contRaw)
                        collectRenderers(contRoot, results)
                        contToken = extractContinuationToken(contRoot)
                        page++
                    }

                    Log.d("BoltPlayer", "Loaded ${results.size} items for browseId=$browseId ($page continuation pages)")
                    results
                }
                videos = items
                isLoading = false
            } catch (e: Exception) {
                Log.e("BoltPlayer", "Library fetch failed: $e")
                errorMessage = e.message?.take(120) ?: "Failed to load"
                isLoading = false
            }
            invalidate()
        }
    }

    /** Finds the first continuationCommand token buried anywhere in the JSON tree. */
    private fun extractContinuationToken(obj: Any): String? {
        return when (obj) {
            is JSONObject -> {
                val token = obj.optJSONObject("continuationItemRenderer")
                    ?.optJSONObject("continuationEndpoint")
                    ?.optJSONObject("continuationCommand")
                    ?.optString("token")?.takeIf { it.isNotBlank() }
                token ?: obj.keys().asSequence().firstNotNullOfOrNull { key ->
                    extractContinuationToken(obj.get(key))
                }
            }
            is JSONArray -> (0 until obj.length()).asSequence()
                .firstNotNullOfOrNull { i -> extractContinuationToken(obj.get(i)) }
            else -> null
        }
    }

    /** Recursively walks the JSON tree collecting all known video renderer nodes. */
    private fun collectRenderers(obj: Any, out: MutableList<VideoItem>) {
        when (obj) {
            is JSONObject -> {
                // Standard search / channel video
                obj.optJSONObject("videoRenderer")
                    ?.let { parseVideoRenderer(it) }?.let { out.add(it) }
                // Watch Later playlist
                obj.optJSONObject("playlistVideoRenderer")
                    ?.let { parseVideoRenderer(it) }?.let { out.add(it) }
                // Watch History
                obj.optJSONObject("compactVideoRenderer")
                    ?.let { parseVideoRenderer(it) }?.let { out.add(it) }
                // Purchased / rented content
                obj.optJSONObject("activityItemRenderer")
                    ?.let { parseActivityRenderer(it) }?.let { out.add(it) }

                obj.keys().forEach { key ->
                    collectRenderers(obj.get(key), out)
                }
            }
            is JSONArray -> {
                for (i in 0 until obj.length()) collectRenderers(obj.get(i), out)
            }
        }
    }

    private fun runsText(obj: JSONObject?, key: String): String =
        obj?.optJSONObject(key)?.optJSONArray("runs")?.let { runs ->
            (0 until runs.length()).joinToString("") { i -> runs.getJSONObject(i).optString("text") }
        } ?: obj?.optJSONObject(key)?.optString("simpleText") ?: ""

    /** Parses standard videoRenderer / playlistVideoRenderer / compactVideoRenderer */
    private fun parseVideoRenderer(vr: JSONObject): VideoItem? {
        return try {
            val videoId = vr.optString("videoId").takeIf { it.isNotBlank() } ?: return null
            val titleText =
                vr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: vr.optJSONObject("title")?.optString("simpleText")
                ?: return null
            val channel =
                vr.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: vr.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: vr.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: ""
            val views =
                vr.optJSONObject("viewCountText")?.optString("simpleText")
                ?: runsText(vr, "viewCountText")
                    .takeIf { it.isNotBlank() }
                ?: vr.optJSONObject("shortViewCountText")?.optString("simpleText")
                ?: runsText(vr, "shortViewCountText")
            val uploadDate =
                vr.optJSONObject("publishedTimeText")?.optString("simpleText")
                ?: runsText(vr, "publishedTimeText")
            val description =
                vr.optJSONArray("detailedMetadataSnippets")?.optJSONObject(0)
                    ?.let { runsText(it, "snippetText") }?.takeIf { it.isNotBlank() }
                ?: runsText(vr, "descriptionSnippet").takeIf { it.isNotBlank() }
                ?: ""
            VideoItem(videoId, titleText, channel, views, uploadDate, description.take(160))
        } catch (e: Exception) { null }
    }

    /**
     * Parses activityItemRenderer used in the Purchases / Memberships page.
     * The videoId is embedded in the watch URL inside onTap.navigateAction.
     */
    private fun parseActivityRenderer(ar: JSONObject): VideoItem? {
        return try {
            val watchUrl =
                ar.optJSONObject("onTap")
                    ?.optJSONObject("navigateAction")
                    ?.optJSONObject("endpoint")
                    ?.optJSONObject("urlEndpoint")
                    ?.optString("url") ?: return null

            val videoId = Regex("""[?&]v=([^&]+)""").find(watchUrl)
                ?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: return null

            val titleText = ar.optJSONObject("title")
                ?.optJSONObject("cardItemTextRenderer")
                ?.optJSONObject("text")
            val title = titleText?.optString("simpleText")?.takeIf { it.isNotBlank() }
                ?: titleText?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
                ?: return null

            val subtitle =
                ar.optJSONObject("subtitle")
                    ?.optJSONObject("cardItemTextRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")
                    ?.let { runs ->
                        (0 until runs.length()).joinToString("") { i ->
                            runs.getJSONObject(i).optString("text")
                        }
                    } ?: ""

            VideoItem(videoId, title, subtitle)
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
                val meta = listOf(v.channel, v.views, v.uploadDate)
                    .filter { it.isNotBlank() }.joinToString(" • ")
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(v.title)
                        .apply { if (meta.isNotBlank()) addText(meta) }
                        .apply { if (v.description.isNotBlank()) addText(v.description) }
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
