package com.heryerde.radyo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder

class RadioService : Service() {
    private var mediaSession: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        mediaSession = MediaSession(this, "RadioSession").apply {
            isActive = true
            // Bluetooth kulaklıktan vs basıldığında koda yönlendirme
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { RadioManager.resume() }
                override fun onPause() { RadioManager.pause() }
                override fun onSkipToNext() { RadioManager.onNextAction?.invoke() }
                override fun onSkipToPrevious() { RadioManager.onPrevAction?.invoke() }
                override fun onStop() {
                    RadioManager.pause()
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                    stopSelf()
                }
            })
        }

        startForeground(1, createNotification("Radyo Hazır", "Müzik keyfi başlıyor...", false))

        RadioManager.onNotificationUpdate = { title, text ->
            val isPlaying = RadioManager.isPlaying.value
            updateMediaSession(title, text, isPlaying)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(1, createNotification(title, text, isPlaying))
        }
    }
    
    private fun updateMediaSession(title: String, text: String, isPlaying: Boolean) {
        mediaSession?.let { session ->
            val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, text)
                .build()
            session.setMetadata(metadata)

            val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            val playbackState = PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
            session.setPlaybackState(playbackState)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_PLAY" -> RadioManager.resume()
            "ACTION_PAUSE" -> RadioManager.pause()
            "ACTION_NEXT" -> RadioManager.onNextAction?.invoke()
            "ACTION_PREV" -> RadioManager.onPrevAction?.invoke()
            "ACTION_STOP" -> {
                RadioManager.pause()
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("radyo_kanal", "Radyo Yayını", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String, isPlaying: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val prevAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_previous), 
            "Önceki",
            PendingIntent.getService(this, 3, Intent(this, RadioService::class.java).setAction("ACTION_PREV"), PendingIntent.FLAG_IMMUTABLE)
        ).build()

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseIntentAction = if (isPlaying) "ACTION_PAUSE" else "ACTION_PLAY"
        val playPauseAction = Notification.Action.Builder(
            Icon.createWithResource(this, playPauseIcon), 
            if (isPlaying) "Duraklat" else "Oynat",
            PendingIntent.getService(this, 1, Intent(this, RadioService::class.java).setAction(playPauseIntentAction), PendingIntent.FLAG_IMMUTABLE)
        ).build()

        val nextAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_next), 
            "Sonraki",
            PendingIntent.getService(this, 4, Intent(this, RadioService::class.java).setAction("ACTION_NEXT"), PendingIntent.FLAG_IMMUTABLE)
        ).build()

        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), 
            "Kapat",
            PendingIntent.getService(this, 2, Intent(this, RadioService::class.java).setAction("ACTION_STOP"), PendingIntent.FLAG_IMMUTABLE)
        ).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "radyo_kanal")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .addAction(prevAction)   // Action 0
            .addAction(playPauseAction) // Action 1
            .addAction(nextAction)   // Action 2
            .addAction(stopAction)   // Action 3

        mediaSession?.let {
            val mediaStyle = Notification.MediaStyle()
                .setMediaSession(it.sessionToken)
                // İleri, oynat ve geri tuşları bildirim dar iken de görünür olacak (Index 0, 1, 2)
                .setShowActionsInCompactView(0, 1, 2) 
            builder.style = mediaStyle
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        RadioManager.onNotificationUpdate = null
        mediaSession?.isActive = false
        mediaSession?.release()
    }
}
