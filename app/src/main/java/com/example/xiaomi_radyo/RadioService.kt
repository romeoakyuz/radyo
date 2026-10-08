package com.example.xiaomi_radyo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import kotlinx.coroutines.*

class RadioService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                PlayerManager.pause(context)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "radio_channel",
                "Radyo Arka Plan Servisi",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
                description = "Radyo kontrolleri kilit ekraninda"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(becomingNoisyReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(becomingNoisyReceiver, filter)
        }

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                super.onLost(network)
                serviceScope.launch(Dispatchers.Main) {
                    if (!PlayerManager.isUserPaused) {
                        RadioStateHolder.statusText.value = "İnternet Bekleniyor..."
                    }
                }
            }
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                serviceScope.launch(Dispatchers.Main) {
                    val currentUrl = RadioStateHolder.currentStreamUrl.value
                    if (currentUrl.isNotBlank() && !PlayerManager.isUserPaused) {
                        PlayerManager.play(this@RadioService, currentUrl)
                    }
                }
            }
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        try { connectivityManager.registerNetworkCallback(request, networkCallback) } catch (e: Exception) {}

        mediaSession = MediaSessionCompat(this, "RadioService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { PlayerManager.resume(this@RadioService) }
                override fun onPause() { PlayerManager.pause(this@RadioService) }
                override fun onSkipToNext() {
                    RadioStateHolder.nextStation(this@RadioService)
                    PlayerManager.play(this@RadioService, RadioStateHolder.currentStreamUrl.value)
                }
                override fun onSkipToPrevious() {
                    RadioStateHolder.prevStation(this@RadioService)
                    PlayerManager.play(this@RadioService, RadioStateHolder.currentStreamUrl.value)
                }
            })
            isActive = true
        }

        serviceScope.launch { RadioStateHolder.currentStationName.collect { updateNotification() } }
        serviceScope.launch { RadioStateHolder.isPlaying.collect { updateNotification() } }
        serviceScope.launch { RadioStateHolder.statusText.collect { updateNotification() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        when (intent?.action) {
            "PREV", "WIDGET_PREV" -> {
                RadioStateHolder.prevStation(this)
                PlayerManager.play(this, RadioStateHolder.currentStreamUrl.value)
            }
            "TOGGLE", "WIDGET_TOGGLE" -> {
                if (RadioStateHolder.isPlaying.value) PlayerManager.pause(this) else PlayerManager.resume(this)
            }
            "NEXT", "WIDGET_NEXT" -> {
                RadioStateHolder.nextStation(this)
                PlayerManager.play(this, RadioStateHolder.currentStreamUrl.value)
            }
            "STOP" -> {
                PlayerManager.pause(this)
                stopForeground(true)
                stopSelf()
                RadioWidgetProvider.updateAllWidgets(this)
                return START_NOT_STICKY
            }
        }
        if (intent?.action != "STOP") updateNotification()
        return START_STICKY
    }

    private fun updateNotification() {
        val isPlaying = RadioStateHolder.isPlaying.value
        val stationName = RadioStateHolder.currentStationName.value
        val statusText = RadioStateHolder.statusText.value

        mediaSession.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, stationName)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, statusText)
            .build())

        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_PLAY_PAUSE)
            .setState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
            .build())

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        val prevIntent = Intent(this, RadioService::class.java).apply { action = "PREV" }
        val pendingPrev = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE)
        val toggleIntent = Intent(this, RadioService::class.java).apply { action = "TOGGLE" }
        val pendingToggle = PendingIntent.getService(this, 2, toggleIntent, PendingIntent.FLAG_IMMUTABLE)
        val nextIntent = Intent(this, RadioService::class.java).apply { action = "NEXT" }
        val pendingNext = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_IMMUTABLE)

        val playPauseIcon = if (isPlaying) R.drawable.ic_custom_pause else R.drawable.ic_custom_play

        // SENIN TASARIMIN - notification_radio.xml
        val customView = RemoteViews(packageName, R.layout.notification_radio)
        customView.setTextViewText(R.id.notif_station, stationName)
        customView.setTextViewText(R.id.notif_status, statusText)
        customView.setImageViewResource(R.id.btn_play_pause, playPauseIcon)
        customView.setOnClickPendingIntent(R.id.btn_prev, pendingPrev)
        customView.setOnClickPendingIntent(R.id.btn_play_pause, pendingToggle)
        customView.setOnClickPendingIntent(R.id.btn_next, pendingNext)

        val notification = NotificationCompat.Builder(this, "radio_channel")
            .setSmallIcon(R.drawable.ic_custom_play)
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView())
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(pendingOpenApp)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .build()

        if (isPlaying) startForeground(1, notification)
        else {
            stopForeground(false)
            getSystemService(NotificationManager::class.java).notify(1, notification)
        }
        RadioWidgetProvider.updateAllWidgets(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(becomingNoisyReceiver) } catch (e: Exception) {}
        try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (e: Exception) {}
        mediaSession.isActive = false
        mediaSession.release()
        serviceScope.cancel()
    }
}
