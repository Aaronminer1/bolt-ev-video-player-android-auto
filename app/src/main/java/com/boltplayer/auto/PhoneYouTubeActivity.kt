package com.boltplayer.auto

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.SurfaceView
import android.widget.*
import android.app.Activity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class PhoneYouTubeActivity : Activity() {

    private val scope = MainScope()
    private var player: ExoPlayer? = null
    private var results: List<StreamInfoItem> = emptyList()

    private lateinit var searchRow: LinearLayout
    private lateinit var searchEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var resultsList: ListView
    private lateinit var surfaceView: SurfaceView
    private lateinit var playerContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NewPipe.init(NewPipeDownloader.getInstance())

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        // Search bar
        searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        searchEdit = EditText(this).apply {
            hint = "Search YouTube..."
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val searchBtn = Button(this).apply {
            text = "Search"
            setOnClickListener { doSearch() }
        }
        searchRow.addView(searchEdit)
        searchRow.addView(searchBtn)

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }

        // Video player surface
        playerContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 400
            )
            visibility = android.view.View.GONE
        }
        surfaceView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val stopBtn = Button(this).apply {
            text = "✕ Stop"
            setBackgroundColor(0xCC000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
            setOnClickListener { stopPlayer() }
        }
        playerContainer.addView(surfaceView)
        playerContainer.addView(stopBtn)

        // Results list
        resultsList = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        root.addView(searchRow)
        root.addView(statusText)
        root.addView(playerContainer)
        root.addView(resultsList)
        setContentView(root)

        // If launched from the head unit with a pre-resolved stream URL, play it immediately.
        handlePlayIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayIntent(intent)
    }

    private fun handlePlayIntent(intent: Intent?) {
        val title = intent?.getStringExtra("video_title") ?: return
        val phoneUrl = intent.getStringExtra("phone_url")

        // Hide search UI so phone becomes a fullscreen player matching the head unit.
        searchRow.visibility = android.view.View.GONE
        resultsList.visibility = android.view.View.GONE
        playerContainer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        playerContainer.visibility = android.view.View.VISIBLE
        statusText.text = "Playing: $title"

        // Car ExoPlayer handles audio → mute this phone player to avoid double-audio echo.
        // Phone player only renders video frames on the phone screen.
        if (phoneUrl != null) {
            playUrl(phoneUrl, title, muteAudio = true)
        } else {
            // Fallback: attach phone surface directly to the shared car player.
            val sharedPlayer = PlaybackController.player
            if (sharedPlayer != null) {
                stopPlayer()
                player = sharedPlayer   // reference only — don't release on stopPlayer
                sharedPlayer.setVideoSurfaceView(surfaceView)
            }
        }
    }

    private fun playUrl(streamUrl: String, title: String, muteAudio: Boolean = false) {
        statusText.text = "Playing: $title"
        stopPlayer()
        playerContainer.visibility = android.view.View.VISIBLE

        val okHttpClient = OkHttpClient.Builder().build()
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(mapOf("User-Agent" to "Mozilla/5.0 (Android; Mobile)"))
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player = exo
        // Mute when launched from car — car ExoPlayer handles audio, phone just shows video
        if (muteAudio) exo.volume = 0f
        // surface must be attached before prepare so the decoder gets a target
        exo.setVideoSurfaceView(surfaceView)
        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.play()

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val name = when (state) {
                    Player.STATE_BUFFERING -> "BUFFERING"; Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"; else -> "IDLE"
                }
                Log.d("BoltPlayer", "Phone ExoPlayer state: $name")
                if (state == Player.STATE_ENDED) statusText.text = "Finished: $title"
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e("BoltPlayer", "Phone ExoPlayer error: ${error.message} cause=${error.cause?.message}")
                statusText.text = "Error: ${error.message?.take(80)}"
            }
        })
    }

    private fun doSearch() {
        val query = searchEdit.text.toString().trim()
        if (query.isBlank()) return
        statusText.text = "Searching..."
        resultsList.adapter = null
        results = emptyList()

        scope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                    val extractor = service.getSearchExtractor(query)
                    extractor.fetchPage()
                    extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
                }
                results = items
                statusText.text = "${items.size} results"

                val adapter = ArrayAdapter(
                    this@PhoneYouTubeActivity,
                    android.R.layout.simple_list_item_2,
                    android.R.id.text1,
                    items.map { it.name }
                ).also { a ->
                    // set secondary text — we rebuild with a custom approach below
                }

                val labels = items.map { item ->
                    val dur = if (item.duration > 0) formatDuration(item.duration) else ""
                    val ch = item.uploaderName ?: ""
                    if (dur.isNotEmpty() && ch.isNotEmpty()) "$ch  •  $dur"
                    else ch.ifEmpty { dur }
                }

                val listAdapter = object : ArrayAdapter<String>(
                    this@PhoneYouTubeActivity,
                    android.R.layout.simple_list_item_2,
                    android.R.id.text1,   // must specify the TextView ID for multi-view layouts
                    items.map { it.name }
                ) {
                    override fun getView(pos: Int, v: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                        val view = super.getView(pos, v, parent)
                        (view.findViewById<TextView>(android.R.id.text2)).text = labels.getOrElse(pos) { "" }
                        return view
                    }
                }

                resultsList.adapter = listAdapter
                resultsList.setOnItemClickListener { _, _, pos, _ ->
                    playItem(items[pos])
                }

            } catch (e: Exception) {
                statusText.text = "Error: ${e.message?.take(80)}"
            }
        }
    }

    private fun playItem(item: StreamInfoItem) {
        statusText.text = "Loading: ${item.name}"
        stopPlayer()

        scope.launch {
            try {
                val streamUrl = withContext(Dispatchers.IO) {
                    val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                    val extractor = service.getStreamExtractor(item.url)
                    var attempt = 0
                    while (true) {
                        try { extractor.fetchPage(); break } catch (e: Exception) {
                            if (attempt++ < 2 && e.message?.contains("reloaded", ignoreCase = true) == true)
                                Thread.sleep(500) else throw e
                        }
                    }
                    // HLS/DASH manifest is most reliable; fall back to progressive streams.
                    @Suppress("DEPRECATION")
                    extractor.hlsUrl.takeIf { it.isNotBlank() }
                        ?: extractor.dashMpdUrl.takeIf { it.isNotBlank() }
                        ?: extractor.videoStreams
                            .filter { !it.isVideoOnly }
                            .maxByOrNull { it.height }?.content?.takeIf { it.isNotBlank() }
                        ?: extractor.videoStreams.maxByOrNull { it.height }?.content?.takeIf { it.isNotBlank() }
                }

                if (streamUrl == null) {
                    statusText.text = "No stream found"
                    return@launch
                }

                playUrl(streamUrl, item.name)

            } catch (e: Exception) {
                statusText.text = "Playback error: ${e.message?.take(80)}"
            }
        }
    }

    private fun stopPlayer() {
        // Only release if this is our own local player, not the shared car player.
        if (player != null && player !== PlaybackController.player) {
            player?.release()
        }
        player = null
        playerContainer.visibility = android.view.View.GONE
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayer()
        scope.cancel()
    }
}
