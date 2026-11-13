package com.lovedrive.auto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver

class LoveDriveMediaService : MediaBrowserServiceCompat() {

    companion object {
        private const val ROOT_ID = "love_drive_root"
        private const val CHANNEL_ID = "love_drive_playback"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_RELOAD = "com.lovedrive.auto.ACTION_RELOAD"
        const val ACTION_TOGGLE = "com.lovedrive.auto.ACTION_TOGGLE"
    }

    private lateinit var mediaSession: MediaSessionCompat
    private val handler = Handler(Looper.getMainLooper())
    private var photos: List<LovePhoto> = emptyList()
    private var currentIndex = 0
    private var isPlaying = false

    private val slideRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying || photos.isEmpty()) return
            advanceBy(1)
            val delayMs = PhotoRepository.getIntervalSeconds(this@LoveDriveMediaService) * 1000L
            handler.postDelayed(this, delayMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "LoveDriveSession").apply {
            setCallback(sessionCallback)
            isActive = true
        }
        sessionToken = mediaSession.sessionToken

        reloadPhotos(startIfAvailable = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        when (intent?.action) {
            ACTION_RELOAD -> reloadPhotos(startIfAvailable = isPlaying)
            ACTION_TOGGLE -> {
                if (isPlaying) pauseSlideshow() else startSlideshow()
            }
        }
        return START_STICKY
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        photos = PhotoRepository.getPhotos(this)
        val stats = PhotoRepository.getAnniversaryStats(this)
        val items = photos.map { photo ->
            val desc = MediaDescriptionCompat.Builder()
                .setMediaId(photo.id)
                .setTitle("Photo ${photo.index} of ${photo.total}")
                .setSubtitle("${stats.daysTogether} Days Together")
                .setIconBitmap(PhotoRepository.loadCarBitmap(photo.file, 256))
                .build()
            MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
        }.toMutableList()
        result.sendResult(items)
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            startSlideshow()
        }

        override fun onPause() {
            pauseSlideshow()
        }

        override fun onStop() {
            pauseSlideshow()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }

        override fun onSkipToNext() {
            advanceBy(1)
            rescheduleIfPlaying()
        }

        override fun onSkipToPrevious() {
            advanceBy(-1)
            rescheduleIfPlaying()
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            photos = PhotoRepository.getPhotos(this@LoveDriveMediaService)
            val idx = photos.indexOfFirst { it.id == mediaId }
            if (idx >= 0) currentIndex = idx
            startSlideshow()
        }
    }

    private fun reloadPhotos(startIfAvailable: Boolean) {
        photos = PhotoRepository.getPhotos(this)
        if (currentIndex >= photos.size) currentIndex = 0
        updateCurrentMetadata()
        notifyChildrenChanged(ROOT_ID)
        if (startIfAvailable && photos.isNotEmpty()) {
            startSlideshow()
        } else {
            updatePlaybackState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
        }
    }

    private fun startSlideshow() {
        photos = PhotoRepository.getPhotos(this)
        if (photos.isEmpty()) {
            isPlaying = false
            updatePlaybackState(PlaybackStateCompat.STATE_NONE)
            return
        }
        isPlaying = true
        updateCurrentMetadata()
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        startForeground(NOTIFICATION_ID, buildNotification())
        rescheduleIfPlaying()
    }

    private fun pauseSlideshow() {
        isPlaying = false
        handler.removeCallbacks(slideRunnable)
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        if (photos.isNotEmpty()) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun rescheduleIfPlaying() {
        handler.removeCallbacks(slideRunnable)
        if (isPlaying && photos.isNotEmpty()) {
            val delayMs = PhotoRepository.getIntervalSeconds(this) * 1000L
            handler.postDelayed(slideRunnable, delayMs)
        }
    }

    private fun advanceBy(delta: Int) {
        if (photos.isEmpty()) return
        currentIndex = ((currentIndex + delta) % photos.size + photos.size) % photos.size
        updateCurrentMetadata()
        updatePlaybackState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildDisplayStrings(photoIndex: Int, total: Int): Pair<String, String> {
        val stats = PhotoRepository.getAnniversaryStats(this)
        val greeting = PhotoRepository.getTimeGreeting("Kayalvizhi")
        val reservedNote = PhotoRepository.getSubtitle(this)
        val title = if (total > 0) {
            "$greeting • $photoIndex/$total"
        } else {
            greeting
        }

        val ord = PhotoRepository.formatOrdinal(stats.nextAnniversaryNumber)
        val annivText = if (stats.isAnniversaryToday) {
            "${stats.daysTogether}d • Happy $ord Anniv!"
        } else {
            "${stats.daysTogether}d • ${stats.daysToNextAnniversary}d to $ord Anniv"
        }
        val subtitle = if (reservedNote.isNotEmpty()) "$reservedNote • $annivText" else annivText
        return title to subtitle
    }

    private fun updateCurrentMetadata() {
        val hideSeekbar = PhotoRepository.getHideSeekbar(this)

        if (photos.isEmpty()) {
            val placeholder = BitmapFactory.decodeResource(resources, R.drawable.ic_love_drive)
            val (title, subtitle) = buildDisplayStrings(0, 0)
            val emptyMeta = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "empty")
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, placeholder)
                .build()
            mediaSession.setMetadata(emptyMeta)
            return
        }

        val photo = photos[currentIndex]
        val art: Bitmap? = PhotoRepository.loadCarBitmap(photo.file, 800, null)
        val intervalMs = PhotoRepository.getIntervalSeconds(this) * 1000L
        val (title, subtitle) = buildDisplayStrings(photo.index, photo.total)

        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, photo.id)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Love Drive")

        if (!hideSeekbar) {
            metaBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, intervalMs)
        }

        if (art != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art)
        }

        mediaSession.setMetadata(metaBuilder.build())
    }

    private fun updatePlaybackState(state: Int) {
        val hideSeekbar = PhotoRepository.getHideSeekbar(this)
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_STOP

        val pos = if (hideSeekbar) PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN else 0L
        val speed = if (state == PlaybackStateCompat.STATE_PLAYING && !hideSeekbar) 1.0f else 0f

        val pbState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, pos, speed)
            .build()

        mediaSession.setPlaybackState(pbState)
    }

    private fun buildNotification(): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this,
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        )
        val playPauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this,
            PlaybackStateCompat.ACTION_PLAY_PAUSE
        )
        val nextIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this,
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        )

        val (title, subtitle) = if (photos.isNotEmpty()) {
            val p = photos[currentIndex]
            buildDisplayStrings(p.index, p.total)
        } else {
            buildDisplayStrings(0, 0)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_love_drive)
            .setContentIntent(launchIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(slideRunnable)
        mediaSession.release()
        super.onDestroy()
    }
}
