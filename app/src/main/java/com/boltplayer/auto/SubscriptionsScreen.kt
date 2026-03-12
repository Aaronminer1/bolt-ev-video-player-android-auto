package com.boltplayer.auto

import android.util.Log
import androidx.car.app.CarContext
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
import java.security.MessageDigest

/**
 * Shows the user's YouTube subscriptions by downloading the OPML export from
 * https://www.youtube.com/subscription_manager?action_takeout=1
 *
 * This endpoint returns an XML file of all subscribed channels when authenticated
 * via session cookies — no Google Cloud Console registration or API key required.
 */
class SubscriptionsScreen(carContext: CarContext) : Screen(carContext) {

    data class Channel(val name: String, val channelId: String)

    private val scope = MainScope()
    private var channels: List<Channel> = emptyList()
    private var isLoading = true
    private var errorMessage: String? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) { fetchSubscriptions() }
            override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
        })
    }

    private fun fetchSubscriptions() {
        val cookies = GoogleAuthManager.getCookies(carContext) ?: run {
            errorMessage = "Not signed in — use Sign In on the Streaming screen"
            isLoading = false
            invalidate()
            return
        }

        scope.launch {
            try {
                val channelList = withContext(Dispatchers.IO) {
                    val sapisid = cookies.split(";")
                        .firstOrNull { it.trim().startsWith("SAPISID=") }
                        ?.substringAfter("SAPISID=")?.trim() ?: ""
                    val timestamp = System.currentTimeMillis() / 1000
                    val sapisidHash = if (sapisid.isNotBlank()) {
                        MessageDigest.getInstance("SHA-1")
                            .digest("$timestamp $sapisid https://www.youtube.com".toByteArray())
                            .joinToString("") { "%02x".format(it) }
                    } else ""

                    val client = OkHttpClient.Builder().followRedirects(true).build()

                    fun browseRequest(bodyJson: String): Request = Request.Builder()
                        .url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                        .post(bodyJson.toRequestBody("application/json".toMediaType()))
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

                    val initialBody = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20260312.06.00","hl":"en","gl":"US"}},"browseId":"FEchannels"}"""
                    val resp = client.newCall(browseRequest(initialBody)).execute()
                    val body = resp.body?.string() ?: ""
                    Log.d("BoltPlayer", "Subscriptions browse code=${resp.code} len=${body.length}")
                    if (!resp.isSuccessful || body.length < 50) throw Exception("Server error ${resp.code}")

                    val root = JSONObject(body)
                    val results = mutableListOf<Channel>()
                    collectChannels(root, results)

                    // Follow continuation tokens to get all subscribed channels
                    var contToken = extractContinuationToken(root)
                    var page = 0
                    while (contToken != null && page < 10) {
                        val contBody = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20260312.06.00","hl":"en","gl":"US"}},"continuation":"$contToken"}"""
                        val contResp = client.newCall(browseRequest(contBody)).execute()
                        val contRaw = contResp.body?.string() ?: break
                        if (!contResp.isSuccessful || contRaw.length < 50) break
                        val contRoot = JSONObject(contRaw)
                        collectChannels(contRoot, results)
                        contToken = extractContinuationToken(contRoot)
                        page++
                    }

                    Log.d("BoltPlayer", "Loaded ${results.size} subscriptions ($page continuation pages)")
                    results
                }
                channels = channelList.sortedBy { it.name.lowercase() }
                isLoading = false
            } catch (e: Exception) {
                Log.e("BoltPlayer", "Subscriptions fetch failed: $e")
                errorMessage = e.message?.take(120) ?: "Failed to load subscriptions"
                isLoading = false
            }
            invalidate()
        }
    }

    private fun extractContinuationToken(obj: Any): String? = when (obj) {
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

    private fun collectChannels(obj: Any, out: MutableList<Channel>) {
        when (obj) {
            is JSONObject -> {
                // Two common renderer types for subscribed channels
                (obj.optJSONObject("gridChannelRenderer")
                    ?: obj.optJSONObject("channelRenderer"))?.let { cr ->
                    val id = cr.optString("channelId").takeIf { it.isNotBlank() }
                    val name = cr.optJSONObject("title")?.optString("simpleText")
                        ?: cr.optJSONObject("displayName")?.optString("simpleText")
                        ?: cr.optJSONArray("title")?.optJSONObject(0)?.optString("text")
                    if (id != null && name != null) out.add(Channel(name, id))
                }
                obj.keys().forEach { key -> collectChannels(obj.get(key), out) }
            }
            is JSONArray -> for (i in 0 until obj.length()) collectChannels(obj.get(i), out)
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle("My Subscriptions (${GoogleAuthManager.getAccountEmail(carContext) ?: ""})")
            .setHeaderAction(Action.BACK)

        if (isLoading) return builder.setLoading(true).build()

        val listBuilder = ItemList.Builder()
        when {
            errorMessage != null ->
                listBuilder.setNoItemsMessage(errorMessage!!)
            channels.isEmpty() ->
                listBuilder.setNoItemsMessage("No subscriptions found")
            else ->
                channels.forEach { ch ->
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(ch.name)
                            .setImage(CarIcon.Builder(
                                IconCompat.createWithResource(carContext, R.drawable.ic_video)
                            ).build())
                            .setOnClickListener {
                                screenManager.push(YouTubeResultsScreen(carContext, ch.name))
                            }
                            .build()
                    )
                }
        }

        return builder.setSingleList(listBuilder.build()).build()
    }
}
