package com.boltplayer.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList

/**
 * Shared helper — extracts streams for a YouTube watch URL and starts playback
 * on the car ExoPlayer. Used by both YouTubeResultsScreen and YouTubeLibraryScreen.
 */
object YoutubePlayer {

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
                val streamUrl = withContext(Dispatchers.IO) {
                    val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                    val extractor = service.getStreamExtractor(videoUrl)
                    var attempt = 0
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
                }

                val okHttpClient = OkHttpClient.Builder().build()
                val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                    .setDefaultRequestProperties(mapOf("User-Agent" to "Mozilla/5.0 (Android; Mobile)"))
                val mediaSourceFactory = DefaultMediaSourceFactory(carContext.applicationContext)
                    .setDataSourceFactory(dataSourceFactory)

                val exo = ExoPlayer.Builder(carContext.applicationContext)
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
                        Log.e("BoltPlayer", "ExoPlayer error: ${error.message}")
                        CarToast.makeText(carContext, "Playback error: ${error.message?.take(60)}", CarToast.LENGTH_LONG).show()
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        Log.d("BoltPlayer", "ExoPlayer state: ${when(state){1->"IDLE";2->"BUFFERING";3->"READY";4->"ENDED";else->"?"}}")
                    }
                })
                exo.setMediaItem(MediaItem.fromUri(android.net.Uri.parse(streamUrl)))
                exo.prepare()
                exo.play()
                PlaybackController.setPlayer(exo, videoTitle)
                screen.screenManager.push(VideoPlayerScreen(carContext, videoTitle))

            } catch (e: Exception) {
                Log.e("BoltPlayer", "YoutubePlayer.play failed: $e")
                CarToast.makeText(carContext, "Error: ${e.message?.take(80) ?: "unknown"}", CarToast.LENGTH_LONG).show()
            }
        }
    }
}
