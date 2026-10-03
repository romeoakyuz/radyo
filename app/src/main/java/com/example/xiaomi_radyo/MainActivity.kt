package com.example.xiaomi_radyo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xiaomi_radyo.ui.theme.Xiaomi_radyoTheme

// Derlemeyi patlatan eksik importlar buraya eklendi:
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioMainScreen() {
    val context = LocalContext.current
    var stationList by remember { mutableStateOf(RadioStateHolder.getSavedStations(context)) }

    var nameInput by remember { mutableStateOf("") }
    var genreInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Xiaomi Radyo - Saf Mimari") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Yeni Radyo Kanalı Ekle", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Kanal Adı") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = genreInput,
                onValueChange = { genreInput = it },
                label = { Text("Tür") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Yayın URL (.m3u8 / .mp3)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

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
                Text("Kanalı Kaydet")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Kayıtlı Favori Kanallar", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(stationList) { station ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = station.name, style = MaterialTheme.typography.bodyLarge)
                            Text(text = "Tür: ${station.genre}", style = MaterialTheme.typography.bodyMedium)
                            Text(text = station.streamUrl, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
