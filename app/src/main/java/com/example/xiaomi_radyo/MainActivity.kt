package com.example.xiaomi_radyo

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.xiaomi_radyo.ui.theme.Xiaomi_radyoTheme

// Medya oynatıcı motorumuz
object PlayerManager {
    var mediaPlayer: MediaPlayer? = null
    
    fun play(url: String) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                }
            }
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(url)
            mediaPlayer?.prepareAsync()
            RadioStateHolder.statusText.value = "Bağlanıyor..."
            
            mediaPlayer?.setOnPreparedListener { 
                it.start()
                RadioStateHolder.isPlaying.value = true
                RadioStateHolder.statusText.value = "Canlı Yayın"
            }
            mediaPlayer?.setOnErrorListener { _, _, _ ->
                RadioStateHolder.statusText.value = "Yayın Hatası!"
                RadioStateHolder.isPlaying.value = false
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        RadioStateHolder.isPlaying.value = false
        RadioStateHolder.statusText.value = "Duraklatıldı"
    }
    
    fun resume() {
        mediaPlayer?.start()
        RadioStateHolder.isPlaying.value = true
        RadioStateHolder.statusText.value = "Canlı Yayın"
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
    
    override fun onDestroy() {
        super.onDestroy()
        PlayerManager.mediaPlayer?.release()
        PlayerManager.mediaPlayer = null
    }
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Xiaomi Radyo") })
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = currentStationName, style = MaterialTheme.typography.titleLarge)
                    Text(text = statusText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(onClick = {
                            RadioStateHolder.prevStation(context)
                            PlayerManager.play(RadioStateHolder.currentStreamUrl.value)
                        }) { Text("<< Geri") }
                        
                        Button(onClick = {
                            if (isPlaying) {
                                PlayerManager.pause()
                            } else {
                                if (statusText == "Duraklatıldı" && PlayerManager.mediaPlayer != null) {
                                    PlayerManager.resume()
                                } else {
                                    PlayerManager.play(RadioStateHolder.currentStreamUrl.value)
                                }
                            }
                        }) { Text(if (isPlaying) "⏸ Duraklat" else "▶ Oynat") }
                        
                        Button(onClick = {
                            RadioStateHolder.nextStation(context)
                            PlayerManager.play(RadioStateHolder.currentStreamUrl.value)
                        }) { Text("İleri >>") }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Yeni Radyo Kanalı Ekle", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Kanal Adı") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = genreInput,
                        onValueChange = { genreInput = it },
                        label = { Text("Tür (Örn: Pop)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Yayın URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (nameInput.isNotBlank() && urlInput.isNotBlank()) {
                                RadioStateHolder.addCustomStation(context, nameInput, genreInput, urlInput)
                                stationList = RadioStateHolder.getSavedStations(context)
                                nameInput = ""
                                genreInput = ""
                                urlInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Listeye Ekle")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Kayıtlı Favori Kanallar", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(stationList) { station ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                // Listeden radyoya tıklanınca otomatik çalmaya başlar
                                RadioStateHolder.currentStationName.value = station.name
                                RadioStateHolder.currentStreamUrl.value = station.streamUrl
                                PlayerManager.play(station.streamUrl)
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = station.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                            Text(text = "Tür: ${station.genre}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
