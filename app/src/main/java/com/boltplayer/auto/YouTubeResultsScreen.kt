package com.boltplayer.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class YouTubeResultsScreen(
    carContext: CarContext,
    private val query: String
) : Screen(carContext) {

    private val scope = MainScope()
    private var results: MutableList<StreamInfoItem> = mutableListOf()
    private var isLoading = true
    private var isLoadingMore = false
    private var errorMessage: String? = null
    private var nextPage: Page? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                fetchResults()
            }
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    private fun fetchResults() {
        scope.launch {
            try {
                val (items, next) = withContext(Dispatchers.IO) {
                    val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                    val extractor = service.getSearchExtractor(query)
                    extractor.fetchPage()
                    val page1 = extractor.initialPage
                    val allItems = page1.items.filterIsInstance<StreamInfoItem>().toMutableList()
                    var pageNext = page1.nextPage
                    // Eagerly fetch a second page so we start with ~40 results
                    if (pageNext != null) {
                        try {
                            val page2 = extractor.getPage(pageNext)
                            allItems += page2.items.filterIsInstance<StreamInfoItem>()
                            pageNext = page2.nextPage
                        } catch (_: Exception) {}
                    }
                    Pair(allItems, pageNext)
                }
                results = items
                nextPage = next
                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Search failed: ${e.message?.take(80) ?: "network error"}"
                isLoading = false
            }
            invalidate()
        }
    }

    private fun loadMore() {
        val page = nextPage ?: return
        if (isLoadingMore) return
        isLoadingMore = true
        invalidate()
        scope.launch {
            try {
                val (items, next) = withContext(Dispatchers.IO) {
                    val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                    val extractor = service.getSearchExtractor(query)
                    extractor.fetchPage()
                    val p = extractor.getPage(page)
                    Pair(p.items.filterIsInstance<StreamInfoItem>(), p.nextPage)
                }
                results.addAll(items)
                nextPage = next
            } catch (e: Exception) {
                Log.e("BoltPlayer", "loadMore failed: $e")
            }
            isLoadingMore = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle("YouTube: \"$query\"")
            .setHeaderAction(Action.BACK)

        if (isLoading) {
            return builder.setLoading(true).build()
        }

        val listBuilder = ItemList.Builder()
        when {
            errorMessage != null ->
                listBuilder.setNoItemsMessage(errorMessage!!)
            results.isEmpty() ->
                listBuilder.setNoItemsMessage("No results found for \"$query\"")
            else ->
                results.forEach { item ->
                    val info = buildString {
                        item.uploaderName?.let { append(it) }
                        if (item.duration > 0) append("  •  ${formatDuration(item.duration)}")
                    }
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(item.name)
                            .addText(info)
                            .setImage(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(carContext, R.drawable.ic_video)
                                ).build()
                            )
                            .setOnClickListener { playVideo(item) }
                            .build()
                    )
                }
        }

        val actionBuilder = Action.Builder()
            .setTitle(if (isLoadingMore) "Loading…" else "Load More")
            .setOnClickListener { if (!isLoadingMore) loadMore() }
            .build()
        if (nextPage != null && errorMessage == null && results.isNotEmpty()) {
            listBuilder.setNoItemsMessage("") // keeps builder happy
        }
        // Add "Load More" as the last row when more pages are available
        if (nextPage != null && errorMessage == null && results.isNotEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(if (isLoadingMore) "Loading more\u2026" else "\u25bc Load More")
                    .setOnClickListener { if (!isLoadingMore) loadMore() }
                    .build()
            )
        }
        return builder.setSingleList(listBuilder.build()).build()
    }

    private fun playVideo(item: StreamInfoItem) {
        CarToast.makeText(carContext, "Loading: ${item.name.take(40)}", CarToast.LENGTH_SHORT).show()
        scope.launch {
            try {
                Log.d("BoltPlayer", "playVideo: extracting streams for ${item.url}")

                data class Streams(val carUrl: String?, val phoneUrl: String?, val isHls: Boolean)

                val streams = withContext(Dispatchers.IO) {
                    val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                    val extractor = service.getStreamExtractor(item.url)

                    // Retry on "needs to be reloaded" (NewPipe 0.24.x/0.25.x first-fetch bug)
                    var attempt = 0
                    while (true) {
                        try {
                            extractor.fetchPage()
                            break
                        } catch (e: Exception) {
                            if (attempt++ < 3 && e.message?.contains("reloaded", ignoreCase = true) == true) {
                                Log.w("BoltPlayer", "fetchPage attempt $attempt failed ('needs reload'), retrying…")
                                Thread.sleep(500)
                            } else {
                                throw e
                            }
                        }
                    }

                    // Log all available streams for diagnosis
                    val hlsUrl = try { extractor.hlsUrl.takeIf { it.isNotBlank() } } catch (e: Exception) { null }
                    val dashUrl = try { extractor.dashMpdUrl.takeIf { it.isNotBlank() } } catch (e: Exception) { null }
                    val audioCount = try { extractor.audioStreams.size } catch (e: Exception) { -1 }
                    val videoCount = try { extractor.videoStreams.size } catch (e: Exception) { -1 }
                    Log.d("BoltPlayer", "Available: hls=${hlsUrl != null}, dash=${dashUrl != null}, audio=$audioCount, video=$videoCount")

                    // Car gets the best muxed video+audio stream (we'll render
                    // video to the NavigationTemplate surface + audio to car speakers).
                    // Phone gets the same stream but will be MUTED to avoid double audio.
                    val bestMuxed = try {
                        @Suppress("DEPRECATION")
                        extractor.videoStreams
                            .filter { !it.isVideoOnly }
                            .maxByOrNull { it.height }
                            ?.content?.takeIf { it.isNotBlank() }
                    } catch (e: Exception) { null }

                    // HLS/DASH are adaptive and handle both audio+video
                    val adaptiveUrl = hlsUrl ?: dashUrl

                    val phoneUrl = adaptiveUrl ?: bestMuxed
                        ?: try {
                            extractor.videoStreams.maxByOrNull { it.height }
                                ?.content?.takeIf { it.isNotBlank() }
                        } catch (e: Exception) { null }

                    // Car uses same stream as phone — video goes to surface, audio to speakers
                    val carUrl = adaptiveUrl ?: bestMuxed ?: phoneUrl

                    Log.d("BoltPlayer", "carUrl=${carUrl?.take(100)}")
                    Streams(carUrl, phoneUrl ?: carUrl, hlsUrl != null || dashUrl != null)
                }

                if (streams.carUrl != null) {
                    Log.d("BoltPlayer", "Creating ExoPlayer for car stream (isHls=${streams.isHls})")

                    // Use OkHttp data source for consistency with NewPipe's downloads
                    val okHttpClient = OkHttpClient.Builder().build()
                    val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                        .setDefaultRequestProperties(
                            mapOf("User-Agent" to "Mozilla/5.0 (Android; Mobile)")
                        )
                    val mediaSourceFactory = DefaultMediaSourceFactory(carContext.applicationContext)
                        .setDataSourceFactory(dataSourceFactory)

                    val exo = ExoPlayer.Builder(carContext.applicationContext)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .build()

                    // Proper audio attributes so Android Auto routes audio to car speakers
                    val audioAttrs = AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build()
                    exo.setAudioAttributes(audioAttrs, /* handleAudioFocus= */ true)

                    exo.addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            Log.e("BoltPlayer", "Car ExoPlayer error: ${error.message} cause=${error.cause?.message}")
                            CarToast.makeText(carContext, "Playback error: ${error.message?.take(60)}", CarToast.LENGTH_LONG).show()
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            val name = when (state) {
                                Player.STATE_IDLE -> "IDLE"; Player.STATE_BUFFERING -> "BUFFERING"
                                Player.STATE_READY -> "READY"; Player.STATE_ENDED -> "ENDED"; else -> "?"
                            }
                            Log.d("BoltPlayer", "Car ExoPlayer state: $name")
                        }
                    })

                    val mediaItem = MediaItem.fromUri(android.net.Uri.parse(streams.carUrl))
                    exo.setMediaItem(mediaItem)
                    exo.prepare()
                    exo.play()
                    PlaybackController.setPlayer(exo, item.name)

                    Log.d("BoltPlayer", "Pushing VideoPlayerScreen")
                    screenManager.push(VideoPlayerScreen(carContext, item.name))
                } else {
                    Log.e("BoltPlayer", "No streams found for ${item.url}")
                    CarToast.makeText(carContext, "No playable stream found", CarToast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("BoltPlayer", "playVideo exception: $e", e)
                CarToast.makeText(
                    carContext,
                    "Error: ${e.message?.take(80) ?: "unknown"}",
                    CarToast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

