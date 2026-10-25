package com.heryerde.radyo

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

object RadioManager {
    var player: ExoPlayer? = null
    var isUserPaused = false
    var currentStationName = mutableStateOf("")
    var currentStationUrl = ""
    var statusText = mutableStateOf("Bir kanal seçin")
    var isPlaying = mutableStateOf(false)
    val retryHandler = Handler(Looper.getMainLooper())
    
    var downloadedBytes = mutableStateOf(0L)
    var playingTimeSeconds = mutableStateOf(0L)
    val timerHandler = Handler(Looper.getMainLooper())
    var isTimerStarted = false

    fun resetDataCounter() { downloadedBytes.value = 0L }
    fun resetTimeCounter() { playingTimeSeconds.value = 0L }
    
    var minBufferSec: Int = 60       
    var maxBufferSec: Int = 180      
    var startThresholdSec: Float = 3.0f 
    var timeoutSec: Int = 90         
    var retryIntervalSec: Long = 5L  
    
    var onNotificationUpdate: ((String, String) -> Unit)? = null

    // YENİ EKLENDİ: Favorileri telefona kalıcı olarak kaydetme ve okuma
    fun saveFavorites(context: Context, favorites: List<Pair<String, String>>) {
        val prefs = context.getSharedPreferences("radyo_prefs", Context.MODE_PRIVATE)
        val data = favorites.joinToString("@@") { "${it.first}||${it.second}" }
        prefs.edit().putString("favorites_data", data).apply()
    }

    private fun loadFavorites(context: Context): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences("radyo_prefs", Context.MODE_PRIVATE)
        val data = prefs.getString("favorites_data", "") ?: ""
        if (data.isEmpty()) {
            // Eğer hiç favori yoksa sadece Kral FM dönsün (Hata vermemesi için)
            return listOf(Pair("Kral FM", "https://ssldyg.radyotvonline.com/smil/smil:kralfm.smil/playlist.m3u8"))
        }
        return data.split("@@").mapNotNull {
            val parts = it.split("||")
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        }
    }

    // YENİ EKLENDİ: İleri tuşu sadece favorilerde gezer
    fun handleNext(context: Context) {
        val favs = loadFavorites(context)
        if (favs.isEmpty()) return
        val idx = favs.indexOfFirst { it.second == currentStationUrl }
        val nextIdx = if (idx != -1 && idx < favs.size - 1) idx + 1 else 0
        playStation(favs[nextIdx].second, favs[nextIdx].first)
    }

    // YENİ EKLENDİ: Geri tuşu sadece favorilerde gezer
    fun handlePrev(context: Context) {
        val favs = loadFavorites(context)
        if (favs.isEmpty()) return
        val idx = favs.indexOfFirst { it.second == currentStationUrl }
        val prevIdx = if (idx > 0) idx - 1 else favs.size - 1
        playStation(favs[prevIdx].second, favs[prevIdx].first)
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isPlaying.value && !isUserPaused) {
                playingTimeSeconds.value++
            }
            timerHandler.postDelayed(this, 1000L)
        }
    }

    fun createPlayer(context: Context) {
        try {
            player?.release()
            
            val startThresholdMs = (startThresholdSec * 1000).toInt().coerceAtLeast(100)
            val minMs = (minBufferSec * 1000).coerceAtLeast(500)
            val maxMs = (maxBufferSec * 1000).coerceAtLeast(minMs)
            val rebufferMs = (startThresholdMs * 2).coerceAtLeast(6000)
            val targetBufferBytes: Int = (maxBufferSec.toLong() * 16L * 1024L * 1024L).coerceIn(1024L, 512L * 1024L * 1024L).toInt()

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(minMs, maxMs, startThresholdMs, rebufferMs)
                .setTargetBufferBytes(targetBufferBytes)
                .build()

            val transferListener = object : TransferListener {
                override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
                override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
                override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                    if (!isUserPaused) {
                        downloadedBytes.value += bytesTransferred
                    }
                }
                override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            }

            val timeoutMs = (timeoutSec * 1000).coerceAtLeast(1000)
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(timeoutMs)
                .setReadTimeoutMs(timeoutMs)
                .setAllowCrossProtocolRedirects(true) 
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .setTransferListener(transferListener) 

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            player = ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()

            setupListener()

            if (!isTimerStarted) {
                isTimerStarted = true
                timerHandler.removeCallbacks(timerRunnable)
                timerHandler.postDelayed(timerRunnable, 1000L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val retryRunnable = object : Runnable {
        override fun run() {
            if (!isUserPaused && currentStationUrl.isNotEmpty()) {
                statusText.value = "İnternet bekleniyor..."
                onNotificationUpdate?.invoke(currentStationName.value, statusText.value)
                try {
                    val mediaItem = MediaItem.fromUri(currentStationUrl)
                    player?.setMediaItem(mediaItem)
                    player?.prepare()
                    player?.play()
                } catch (e: Exception) {}
                
                retryHandler.postDelayed(this, (retryIntervalSec * 1000L).coerceAtLeast(2000L))
            }
        }
    }

    private fun setupListener() {
        player?.addListener(object : Player.Listener {
            
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                val title = mediaMetadata.title?.toString()
                val displayTitle = mediaMetadata.displayTitle?.toString()
                val artist = mediaMetadata.artist?.toString()

                val songInfo = when {
                    !title.isNullOrBlank() && !artist.isNullOrBlank() -> "$artist - $title"
                    !title.isNullOrBlank() -> title
                    !displayTitle.isNullOrBlank() -> displayTitle
                    else -> ""
                }

                if (songInfo.isNotBlank()) {
                    statusText.value = songInfo
                    onNotificationUpdate?.invoke(currentStationName.value, statusText.value)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!isUserPaused) {
                    statusText.value = "Bağlantı koptu..."
                    isPlaying.value = true
                    onNotificationUpdate?.invoke(currentStationName.value, statusText.value)
                    retryHandler.removeCallbacks(retryRunnable)
                    retryHandler.postDelayed(retryRunnable, 1000L)
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying.value = !isUserPaused

                if (playbackState == Player.STATE_READY && player?.playWhenReady == true) {
                    if (statusText.value == "Bağlanıyor..." || statusText.value == "İnternet bekleniyor...") {
                        statusText.value = "Oynatılıyor..."
                    }
                    retryHandler.removeCallbacks(retryRunnable)
                } else if (playbackState == Player.STATE_BUFFERING && !isUserPaused) {
                    statusText.value = "Bağlanıyor..."
                }
                onNotificationUpdate?.invoke(currentStationName.value, statusText.value)
            }
            
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                if (isPlayingState) {
                    retryHandler.removeCallbacks(retryRunnable)
                } else {
                    if (player?.playWhenReady == false) {
                        isUserPaused = true
                        statusText.value = "Durduruldu"
                        retryHandler.removeCallbacks(retryRunnable)
                    }
                }
                isPlaying.value = !isUserPaused
                onNotificationUpdate?.invoke(currentStationName.value, statusText.value)
            }
        })
    }

    fun playStation(url: String, name: String) {
        try {
            isUserPaused = false
            currentStationUrl = url
            currentStationName.value = name
            val mediaItem = MediaItem.fromUri(url)
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.play()
            statusText.value = "Bağlanıyor..."
            
            isPlaying.value = true
            retryHandler.removeCallbacks(retryRunnable)
            onNotificationUpdate?.invoke(name, statusText.value)
        } catch (e: Exception) {
            statusText.value = "Oynatma hatası!"
        }
    }

    fun pause() {
        try {
            isUserPaused = true
            player?.pause()
            isPlaying.value = false
            statusText.value = "Durduruldu"
            retryHandler.removeCallbacks(retryRunnable)
            onNotificationUpdate?.invoke(currentStationName.value, statusText.value)
        } catch (e: Exception) {}
    }

    fun resume() {
        try {
            isUserPaused = false
            if (currentStationUrl.isNotEmpty()) {
                val mediaItem = MediaItem.fromUri(currentStationUrl)
                player?.setMediaItem(mediaItem)
                player?.prepare()
                player?.play()
                
                isPlaying.value = true
                statusText.value = "Bağlanıyor..."
                onNotificationUpdate?.invoke(currentStationName.value, statusText.value)
            }
        } catch (e: Exception) {}
    }
}
