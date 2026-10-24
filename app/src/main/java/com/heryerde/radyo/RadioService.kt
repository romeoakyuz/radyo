package com.heryerde.radyo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder

class RadioService : Service() {
    private var mediaSession: MediaSession? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pauseAndHideNotification()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(noisyReceiver, filter)
        }
        
        mediaSession = MediaSession(this, "RadioSession").apply {
            isActive = true
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (RadioManager.player == null) {
                        RadioManager.createPlayer(applicationContext)
                    }
                    val stationName = RadioManager.currentStationName.value.ifEmpty { "Kral FM" }
                    if (RadioManager.player?.mediaItemCount == 0) {
                        RadioManager.playStation("https://ssldyg.radyotvonline.com/smil/smil:kralfm.smil/playlist.m3u8", stationName)
                    } else {
                        RadioManager.resume()
                    }
                    
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val notification = createNotification(stationName, "Çalınıyor...", true)
                    startForeground(1, notification)
                    manager.notify(1, notification)
                }
                
                override fun onPause() { pauseAndHideNotification() }
                override fun onSkipToNext() { RadioManager.onNextAction?.invoke() }
                override fun onSkipToPrevious() { RadioManager.onPrevAction?.invoke() }
                override fun onStop() { pauseAndHideNotification() }
            })
        }

        startForeground(1, createNotification("Radyo Hazır", "Müzik keyfi başlıyor...", false))

        RadioManager.onNotificationUpdate = { title, text ->
            val isPlaying = RadioManager.isPlaying.value
            updateMediaSession(title, text, isPlaying)
            
            RadioWidgetProvider.updateAllWidgets(applicationContext)

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (isPlaying) {
                manager.notify(1, createNotification(title, text, true))
            }
        }
    }

    private fun pauseAndHideNotification() {
        RadioManager.pause()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
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
        val currentName = RadioManager.currentStationName.value.ifEmpty { "Radyo" }
        val isPlaying = RadioManager.isPlaying.value
        startForeground(1, createNotification(currentName, if (isPlaying) "Çalınıyor..." else "Durduruldu", isPlaying))

        when (intent?.action) {
            "ACTION_PLAY" -> {
                if (RadioManager.player == null) {
                    RadioManager.createPlayer(applicationContext)
                }
                val stationName = RadioManager.currentStationName.value.ifEmpty { "Kral FM" }
                if (RadioManager.player?.mediaItemCount == 0) {
                    val urlToPlay = RadioManager.currentStationUrl.ifEmpty { "https://ssldyg.radyotvonline.com/smil/smil:kralfm.smil/playlist.m3u8" }
                    RadioManager.playStation(urlToPlay, stationName)
                } else {
                    RadioManager.resume()
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notification = createNotification(stationName, "Çalınıyor...", true)
                manager.notify(1, notification)
            }
            "ACTION_PAUSE", "ACTION_STOP" -> { 
                pauseAndHideNotification() 
            }
            "ACTION_NEXT" -> { 
                RadioManager.onNextAction?.invoke() 
            }
            "ACTION_PREV" -> { 
                RadioManager.onPrevAction?.invoke() 
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
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)

        mediaSession?.let {
            val mediaStyle = Notification.MediaStyle()
                .setMediaSession(it.sessionToken)
                .setShowActionsInCompactView(0, 1, 2) 
            builder.style = mediaStyle
        }

        return builder.build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // KRİTİK EKLENTİ: Eğer radyo durdurulmuşsa ve uygulama kapatılırsa servisi tamamen öldür.
        if (!RadioManager.isPlaying.value) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(noisyReceiver)
        } catch (e: Exception) {}
        RadioManager.onNotificationUpdate = null
        mediaSession?.isActive = false
        mediaSession?.release()
    }
}
