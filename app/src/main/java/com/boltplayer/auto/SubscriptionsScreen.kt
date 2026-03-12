package com.boltplayer.auto

import android.util.Log
import android.util.Xml
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request

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
                    val client = OkHttpClient()
                    val req = Request.Builder()
                        .url("https://www.youtube.com/subscription_manager?action_takeout=1")
                        .header("Cookie", cookies)
                        .header("User-Agent",
                            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36")
                        .build()

                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string() ?: ""

                    Log.d("BoltPlayer", "OPML response code=${resp.code} length=${body.length}")

                    // If we got redirected to accounts.google.com, session expired
                    if (resp.code == 302 || body.contains("accounts.google.com") ||
                        body.contains("\"error\"") || body.length < 100) {
                        GoogleAuthManager.signOut(carContext)
                        throw Exception("Session expired — please sign in again")
                    }

                    parseOpml(body)
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

    /**
     * Parses the OPML XML exported by YouTube's subscription_manager endpoint.
     * Structure:
     *   <outline text="YouTube Subscriptions">
     *     <outline text="Channel Name" xmlUrl="...?channel_id=UCxxxxx" />
     *   </outline>
     */
    private fun parseOpml(xml: String): List<Channel> {
        val result = mutableListOf<Channel>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(xml.reader())
            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                    parser.name == "outline") {
                    val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                    val title  = parser.getAttributeValue(null, "text")
                        ?: parser.getAttributeValue(null, "title")
                    if (xmlUrl != null && title != null && xmlUrl.contains("channel_id=")) {
                        val channelId = xmlUrl.substringAfter("channel_id=").substringBefore("&")
                        result.add(Channel(title, channelId))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("BoltPlayer", "OPML parse error: $e")
        }
        Log.d("BoltPlayer", "Parsed ${result.size} subscriptions from OPML")
        return result
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
