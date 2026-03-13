package com.boltplayer.auto

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.car.app.CarContext
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
import kotlinx.coroutines.*

class FileBrowserScreen(carContext: CarContext) : Screen(carContext) {

    data class VideoItem(
        val id: Long,
        val uri: Uri,
        val title: String,
        val duration: Long,
        val size: Long
    )

    private val scope = MainScope()
    private var videos: List<VideoItem> = emptyList()
    private var isLoading = true

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                scope.launch {
                    videos = withContext(Dispatchers.IO) { scanVideos() }
                    isLoading = false
                    invalidate()
                }
            }
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        if (isLoading) {
            return ListTemplate.Builder()
                .setTitle("Bolt Player")
                .setHeaderAction(Action.APP_ICON)
                .setLoading(true)
                .build()
        }

        val listBuilder = ItemList.Builder()

        // Streaming option always at top
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Streaming")
                .addText("YouTube, direct URLs, IPTV")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.ic_play)
                    ).build()
                )
                .setOnClickListener {
                    screenManager.push(StreamingScreen(carContext))
                }
                .build()
        )

        // Mirror phone screen
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Mirror Phone Screen")
                .addText("Cast your phone display to the car. Note: DRM apps (Netflix, etc.) will appear black.")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.ic_play)
                    ).build()
                )
                .setOnClickListener {
                    screenManager.push(ScreenMirrorScreen(carContext))
                }
                .build()
        )

        // Native browser
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Browser")
                .addText("Browse the web directly on the car screen.")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.ic_play)
                    ).build()
                )
                .setOnClickListener {
                    screenManager.push(WebBrowserScreen(carContext))
                }
                .build()
        )

        if (videos.isEmpty()) {
            listBuilder.setNoItemsMessage(
                "No local videos found. Grant storage permission on your phone and add videos."
            )
        } else {
            val fallbackIcon = CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_video)
            ).build()

            videos.take(99).forEach { video ->
                val row = Row.Builder()
                    .setTitle(video.title)
                    .addText("${formatDuration(video.duration)}  •  ${formatSize(video.size)}")
                    .setImage(fallbackIcon)
                    .setOnClickListener {
                        val exo = androidx.media3.exoplayer.ExoPlayer
                            .Builder(carContext.applicationContext).build()
                        exo.setMediaItem(
                            androidx.media3.common.MediaItem.fromUri(video.uri)
                        )
                        exo.prepare()
                        exo.play()
                        PlaybackController.setPlayer(exo, video.title)
                        screenManager.push(VideoPlayerScreen(carContext, video.title))
                    }
                    .build()
                listBuilder.addItem(row)
            }
        }

        return ListTemplate.Builder()
            .setTitle("Bolt Player")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }

    private fun scanVideos(): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        try {
            carContext.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Unknown"
                    val duration = cursor.getLong(durationCol)
                    val size = cursor.getLong(sizeCol)

                    val uri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    videos.add(VideoItem(id, uri, name, duration, size))
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted — return empty list
        }

        return videos
    }

    private fun loadThumbnail(videoId: Long, uri: Uri): CarIcon? {
        return try {
            val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                carContext.contentResolver.loadThumbnail(uri, Size(128, 128), null)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    carContext.contentResolver,
                    videoId,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            }
            bitmap?.let {
                CarIcon.Builder(IconCompat.createWithBitmap(it)).build()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
            else -> "%.0f KB".format(bytes / 1_024.0)
        }
    }
}
