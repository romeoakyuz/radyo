package com.example.xiaomi_radyo

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.xiaomi_radyo.ui.theme.Xiaomi_radyoTheme
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

object PlayerManager {
    var mediaPlayer: MediaPlayer? = null
    var isUserPaused = true
    var wasInterruptedBySystem = false
    private var audioFocusRequest: Any? = null
    private var appContext: Context? = null
    private var ignoreFocusLossUntil = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val context = appContext ?: return@OnAudioFocusChangeListener
        if (System.currentTimeMillis() < ignoreFocusLossUntil) {
            return@OnAudioFocusChangeListener
        }
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (!isUserPaused && RadioStateHolder.isPlaying.value) {
                    wasInterruptedBySystem = true
                    silentPause(context)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasInterruptedBySystem && !isUserPaused) {
                    wasInterruptedBySystem = false
                    resume(context)
                }
            }
        }
    }

    private fun requestAudioFocus(context: Context): Boolean {
        appContext = context.applicationContext
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            audioFocusRequest = focusRequest
            return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            return audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest is AudioFocusRequest) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest as AudioFocusRequest)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }
    
    fun play(context: Context, url: String) {
        isUserPaused = false
        wasInterruptedBySystem = false
        appContext = context.applicationContext 
        
        RadioStateHolder.isPlaying.value = true
        RadioStateHolder.statusText.value = "Bağlanıyor..."
        startBackgroundService(appContext!!)
        
        cancelRetry()
        ignoreFocusLossUntil = System.currentTimeMillis() + 2500
        requestAudioFocus(appContext!!)
        
        try {
            mediaPlayer?.setOnPreparedListener(null)
            mediaPlayer?.setOnErrorListener(null)
            mediaPlayer?.release()
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
            }
            
            mediaPlayer?.setDataSource(url)
            mediaPlayer?.prepareAsync()
            
            mediaPlayer?.setOnPreparedListener { 
                it.start()
                RadioStateHolder.statusText.value = "Canlı Yayın"
                cancelRetry()
            }
            
            mediaPlayer?.setOnErrorListener { _, _, _ ->
                handleConnectionStall(url) 
                true
            }
            scheduleRetry(url)
        } catch (e: Exception) {
            e.printStackTrace()
            handleConnectionStall(url)
        }
    }

    private fun scheduleRetry(url: String) {
        cancelRetry()
        retryRunnable = Runnable {
            if (!isUserPaused && !RadioStateHolder.isPlaying.value) {
                RadioStateHolder.statusText.value = "Yeniden Bağlanılıyor..."
                appContext?.let { play(it, url) }
            }
        }
        handler.postDelayed(retryRunnable!!, 7000)
    }

    private fun cancelRetry() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
    }

    private fun handleConnectionStall(url: String) {
        RadioStateHolder.statusText.value = "İnternet Bekleniyor..."
        RadioStateHolder.isPlaying.value = false
        try {
            mediaPlayer?.setOnPreparedListener(null)
            mediaPlayer?.setOnErrorListener(null)
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}
        
        if (!isUserPaused) {
            handler.postDelayed({
                if (!isUserPaused) {
                    appContext?.let { play(it, url) }
                }
            }, 5000)
        }
    }

    fun pause(context: Context) {
        isUserPaused = true
        wasInterruptedBySystem = false
        cancelRetry()
        abandonAudioFocus(context)
        try {
            mediaPlayer?.setOnPreparedListener(null)
            mediaPlayer?.setOnErrorListener(null)
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}
        RadioStateHolder.isPlaying.value = false
        RadioStateHolder.statusText.value = "Duraklatıldı"
        stopBackgroundService(context)
    }

    private fun silentPause(context: Context) {
        cancelRetry()
        abandonAudioFocus(context)
        try {
            mediaPlayer?.setOnPreparedListener(null)
            mediaPlayer?.setOnErrorListener(null)
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}
        RadioStateHolder.isPlaying.value = false
        RadioStateHolder.statusText.value = "Duraklatıldı"
        stopBackgroundService(context)
    }
    
    fun resume(context: Context) {
        isUserPaused = false
        wasInterruptedBySystem = false
        play(context, RadioStateHolder.currentStreamUrl.value)
    }

    private fun startBackgroundService(context: Context) {
        val safeContext = appContext ?: context.applicationContext
        val intent = Intent(safeContext, RadioService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeContext.startForegroundService(intent)
        } else {
            safeContext.startService(intent)
        }
    }

    private fun stopBackgroundService(context: Context) {
        val safeContext = appContext ?: context.applicationContext
        val intent = Intent(safeContext, RadioService::class.java)
        safeContext.stopService(intent)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            Xiaomi_radyoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RadioMainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (PlayerManager.wasInterruptedBySystem && !PlayerManager.isUserPaused) {
            PlayerManager.wasInterruptedBySystem = false
            PlayerManager.resume(this)
        }
    }
}

fun checkNotificationPerm(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
}

fun checkBatteryPerm(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else true
}

@Composable
fun CustomCompactInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
            .height(40.dp)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)))
                }
                innerTextField()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioMainScreen() {
    val context = LocalContext.current
    var stationList by remember { mutableStateOf(RadioStateHolder.getSavedStations(context)) }

    val currentStationName by RadioStateHolder.currentStationName.collectAsState()
    val isPlaying by RadioStateHolder.isPlaying.collectAsState()
    val statusText by RadioStateHolder.statusText.collectAsState()

    var nameInput by remember { mutableStateOf("") }
    var genreInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }

    var selectedTab by remember { mutableStateOf(0) }

    val prefs = context.getSharedPreferences("xiaomi_radyo_prefs", Context.MODE_PRIVATE)
    var hasNotif by remember { mutableStateOf(checkNotificationPerm(context)) }
    var hasBatt by remember { mutableStateOf(checkBatteryPerm(context)) }
    var hasAuto by remember { mutableStateOf(prefs.getBoolean("auto_start_prompted", false)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotif = checkNotificationPerm(context)
                hasBatt = checkBatteryPerm(context)
                hasAuto = prefs.getBoolean("auto_start_prompted", false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Radyo") }) },
        bottomBar = {
            Column {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = currentStationName, style = MaterialTheme.typography.titleMedium)
                        Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(onClick = {
                                RadioStateHolder.prevStation(context)
                                PlayerManager.play(context, RadioStateHolder.currentStreamUrl.value)
                            }) { Text("<<") }
                            
                            Button(onClick = {
                                if (isPlaying) {
                                    PlayerManager.pause(context)
                                } else {
                                    PlayerManager.resume(context)
                                }
                            }) { Text(if (isPlaying) "⏸ Duraklat" else "▶ Oynat") }
                            
                            Button(onClick = {
                                RadioStateHolder.nextStation(context)
                                PlayerManager.play(context, RadioStateHolder.currentStreamUrl.value)
                            }) { Text(">>") }
                        }
                    }
                }
                
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Favoriler") },
                        label = { Text("Favoriler") },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.List, contentDescription = "Tüm Kanallar") },
                        label = { Text("Kanallar") },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Ayarlar") },
                        label = { Text("Ayarlar") },
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 }
                    )
                }
            }
        }
    ) { padding ->
        
        if (selectedTab == 0) {
            var localFavs by remember(stationList) { mutableStateOf(stationList.filter { it.isFavorite }) }
            
            val favState = rememberReorderableLazyListState(
                onMove = { from, to ->
                    val fromIdx = from.index - 1
                    val toIdx = to.index - 1
                    if (fromIdx in localFavs.indices && toIdx in localFavs.indices) {
                        localFavs = localFavs.toMutableList().apply {
                            add(toIdx, removeAt(fromIdx))
                        }
                    }
                },
                onDragEnd = { _, _ ->
                    val newStationList = stationList.toMutableList()
                    val favIndices = newStationList.mapIndexedNotNull { index, it -> if (it.isFavorite) index else null }
                    favIndices.forEachIndexed { i, globalIdx ->
                        newStationList[globalIdx] = localFavs[i]
                    }
                    stationList = newStationList
                    RadioStateHolder.saveStations(context, stationList)
                }
            )

            LazyColumn(
                state = favState.listState,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).reorderable(favState)
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Text("Favori Kanallarınız", style = MaterialTheme.typography.titleMedium)
                        Text("Yeniden sıralamak için kartlara basılı tutun", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                items(localFavs, key = { it.id }) { station ->
                    ReorderableItem(favState, key = station.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 12.dp else 0.dp)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).shadow(elevation).detectReorderAfterLongPress(favState)
                                .clickable {
                                    RadioStateHolder.currentStationName.value = station.name
                                    RadioStateHolder.currentStreamUrl.value = station.streamUrl
                                    PlayerManager.play(context, station.streamUrl)
                                }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = station.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text(text = "Tür: ${station.genre}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        } else if (selectedTab == 1) {
            var localAll by remember(stationList) { mutableStateOf(stationList) }
            
            val allState = rememberReorderableLazyListState(
                onMove = { from, to ->
                    val fromIdx = from.index - 1
                    val toIdx = to.index - 1
                    if (fromIdx in localAll.indices && toIdx in localAll.indices) {
                        localAll = localAll.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
                    }
                },
                onDragEnd = { _, _ ->
                    stationList = localAll
                    RadioStateHolder.saveStations(context, stationList)
                }
            )

            LazyColumn(
                state = allState.listState,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).reorderable(allState)
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Yeni Kanal Ekle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CustomCompactInput(
                                        value = nameInput,
                                        onValueChange = { nameInput = it },
                                        placeholder = "Kanal Adı",
                                        modifier = Modifier.weight(1.5f)
                                    )
                                    CustomCompactInput(
                                        value = genreInput,
                                        onValueChange = { genreInput = it },
                                        placeholder = "Tür",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CustomCompactInput(
                                        value = urlInput,
                                        onValueChange = { urlInput = it },
                                        placeholder = "Yayın URL",
                                        modifier = Modifier.weight(2.5f)
                                    )
                                    Button(
                                        onClick = {
                                            if (nameInput.isNotBlank() && urlInput.isNotBlank()) {
                                                RadioStateHolder.addCustomStation(context, nameInput, genreInput, urlInput)
                                                stationList = RadioStateHolder.getSavedStations(context)
                                                nameInput = ""; genreInput = ""; urlInput = ""
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) { Text("Ekle", style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Tüm Kanallar", style = MaterialTheme.typography.titleMedium)
                        Text("Sıralamak için basılı tutun", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                items(localAll, key = { it.id }) { station ->
                    ReorderableItem(allState, key = station.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 12.dp else 0.dp)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).shadow(elevation).detectReorderAfterLongPress(allState)
                                .clickable {
                                    RadioStateHolder.currentStationName.value = station.name
                                    RadioStateHolder.currentStreamUrl.value = station.streamUrl
                                    PlayerManager.play(context, station.streamUrl)
                                }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = station.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(text = "Tür: ${station.genre}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = {
                                    RadioStateHolder.toggleFavorite(context, station.id)
                                    stationList = RadioStateHolder.getSavedStations(context)
                                }) { Icon(if (station.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favori", tint = if (station.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                                IconButton(onClick = {
                                    RadioStateHolder.deleteStation(context, station.id)
                                    stationList = RadioStateHolder.getSavedStations(context)
                                }) { Icon(Icons.Filled.Delete, "Sil", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Uygulama İzinleri", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bildirim İzni", style = MaterialTheme.typography.titleMedium)
                            Text("Arka planda çalışması için gereklidir.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (hasNotif) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Aktif", tint = Color(0xFF4CAF50))
                        } else {
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    ActivityCompat.requestPermissions(context as ComponentActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                                }
                            }) { Text("İzin Ver") }
                        }
                    }
                }
                
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pil Optimizasyonu", style = MaterialTheme.typography.titleMedium)
                            Text("Ekran kapalıyken yayının kesilmesini engeller.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (hasBatt) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Aktif", tint = Color(0xFF4CAF50))
                        } else {
                            Button(onClick = {
                                val intent = Intent().apply {
                                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }) { Text("Kapat") }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Otomatik Başlatma", style = MaterialTheme.typography.titleMedium)
                            Text("Xiaomi cihazlarda teybe otomatik bağlanmayı sağlar.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (hasAuto) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Aktif", tint = Color(0xFF4CAF50))
                        } else {
                            Button(onClick = {
                                try {
                                    val intent = Intent().apply {
                                        component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                                    }
                                    context.startActivity(intent)
                                    prefs.edit().putBoolean("auto_start_prompted", true).apply()
                                    hasAuto = true
                                } catch (e: Exception) {}
                            }) { Text("Ayarla") }
                        }
                    }
                }
            }
        }
    }
}
