package com.heryerde.radyo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.Locale

val DarkBg = Color(0xFF1B1D22)
val CardBg = Color(0xFF2A2D35)
val Orange = Color(0xFFF26B24)
val BottomBarBg = Color(0xFF26282F)
val TabActiveBg = Color(0xFF353942)

data class RadioStation(val name: String, val genre: String, val url: String, val isFavorite: Boolean = false)

fun formatDataUsage(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

fun formatTimeDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, secs)
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val serviceIntent = Intent(this, RadioService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        if (RadioManager.player == null) {
            RadioManager.createPlayer(this)
        }

        setContent {
            val context = LocalContext.current
            
            LaunchedEffect(RadioManager.isPlaying.value, RadioManager.currentStationName.value) {
                RadioWidgetProvider.updateAllWidgets(context)
            }

            val stationsState = remember {
                mutableStateListOf(
                    RadioStation("Kral FM", "Tür: Damar & Arabesk", "https://ssldyg.radyotvonline.com/smil/smil:kralfm.smil/playlist.m3u8", true),
                    RadioStation("Kral FM (WMP)", "Tür: Canlı / Alternatif", "http://kralwmp.radyotvonline.com/", true),
                    RadioStation("Alem FM", "Tür: Pop & Canlı Müzik", "http://scturkmedya.radyotvonline.com/stream/80/", true),
                    RadioStation("Best FM", "Tür: Ulusal / Pop", "https://babaradyo.turkhosted.com/best/bestfm.stream/playlist.m3u8", true)
                )
            }

            var selectedTab by remember { mutableStateOf(2) } 
            var activeSettingDialog by remember { mutableStateOf<Int?>(null) }
            var currentIndex by remember { mutableStateOf(0) }

            var startThresholdSec by remember { mutableStateOf(RadioManager.startThresholdSec.toString()) }
            var minBufferSec by remember { mutableStateOf(RadioManager.minBufferSec.toString()) }
            var maxBufferSec by remember { mutableStateOf(RadioManager.maxBufferSec.toString()) }
            var timeoutSec by remember { mutableStateOf(RadioManager.timeoutSec.toString()) }
            var retryIntervalSec by remember { mutableStateOf(RadioManager.retryIntervalSec.toString()) }
            var saveMessage by remember { mutableStateOf("") }

            val playStation: (Int) -> Unit = { index ->
                if (stationsState.isNotEmpty() && index in stationsState.indices) {
                    currentIndex = index
                    val station = stationsState[currentIndex]
                    RadioManager.playStation(station.url, station.name)
                }
            }
            
            RadioManager.onNextAction = {
                if (stationsState.isNotEmpty()) playStation(if (currentIndex < stationsState.size - 1) currentIndex + 1 else 0)
            }
            RadioManager.onPrevAction = {
                if (stationsState.isNotEmpty()) playStation(if (currentIndex > 0) currentIndex - 1 else stationsState.size - 1)
            }

            Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (selectedTab) {
                            0 -> "Ayarlar"
                            1 -> "Kanallar"
                            else -> "Favoriler"
                        }, 
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(95.dp)
                                .background(CardBg, shape = RoundedCornerShape(8.dp))
                                .clickable { 
                                    RadioManager.resetTimeCounter()
                                    RadioManager.resetDataCounter()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Süre", color = Color.Gray, fontSize = 10.sp)
                                Text(formatTimeDuration(RadioManager.playingTimeSeconds.value), color = Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(105.dp)
                                .background(CardBg, shape = RoundedCornerShape(8.dp))
                                .clickable { 
                                    RadioManager.resetDataCounter()
                                    RadioManager.resetTimeCounter()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Veri", color = Color.Gray, fontSize = 10.sp)
                                Text(formatDataUsage(RadioManager.downloadedBytes.value), color = Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (selectedTab == 2) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Favori Kanallarınız", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (selectedTab == 1) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Tüm Kanallar", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (selectedTab == 0) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), 
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(onClick = { activeSettingDialog = 0 }, colors = ButtonDefaults.buttonColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 16.dp)) { Text("ℹ️ Genel Bilgiler", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        Button(onClick = { activeSettingDialog = 1 }, colors = ButtonDefaults.buttonColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 16.dp)) { Text("🛜 İnternet Ayarları", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        Button(onClick = { activeSettingDialog = 2 }, colors = ButtonDefaults.buttonColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 16.dp)) { Text("🔒 Sistem İzinleri", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        Button(onClick = { activeSettingDialog = 3 }, colors = ButtonDefaults.buttonColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 16.dp)) { Text("➕ Yeni Kanal Ekle", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                    when (selectedTab) {
                        2 -> {
                            val favoriteList = stationsState.mapIndexed { idx, station -> idx to station }.filter { it.second.isFavorite }
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(favoriteList) { favIndex, (origIndex, station) ->
                                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                                        Row(modifier = Modifier.fillMaxWidth().clickable { playStation(origIndex) }.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(station.name, color = Orange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (favIndex > 0) {
                                                    IconButton(onClick = {
                                                        val prevOrigIndex = favoriteList[favIndex - 1].first
                                                        val temp = stationsState[origIndex]
                                                        stationsState[origIndex] = stationsState[prevOrigIndex]
                                                        stationsState[prevOrigIndex] = temp
                                                        if (currentIndex == origIndex) currentIndex = prevOrigIndex else if (currentIndex == prevOrigIndex) currentIndex = origIndex
                                                    }, modifier = Modifier.size(32.dp)) { Text("🔼") }
                                                }
                                                if (favIndex < favoriteList.size - 1) {
                                                    IconButton(onClick = {
                                                        val nextOrigIndex = favoriteList[favIndex + 1].first
                                                        val temp = stationsState[origIndex]
                                                        stationsState[origIndex] = stationsState[nextOrigIndex]
                                                        stationsState[nextOrigIndex] = temp
                                                        if (currentIndex == origIndex) currentIndex = nextOrigIndex else if (currentIndex == nextOrigIndex) currentIndex = origIndex
                                                    }, modifier = Modifier.size(32.dp)) { Text("🔽") }
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(onClick = { stationsState[origIndex] = station.copy(isFavorite = false) }, modifier = Modifier.size(32.dp)) { Text("❤️", fontSize = 18.sp) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(stationsState) { index, station ->
                                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                                        Row(modifier = Modifier.fillMaxWidth().clickable { playStation(index) }.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(station.name, color = Orange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (index > 0) {
                                                    IconButton(onClick = {
                                                        val temp = stationsState[index]
                                                        stationsState[index] = stationsState[index - 1]
                                                        stationsState[index - 1] = temp
                                                        if (currentIndex == index) currentIndex-- else if (currentIndex == index - 1) currentIndex++
                                                    }, modifier = Modifier.size(32.dp)) { Text("🔼") }
                                                }
                                                if (index < stationsState.size - 1) {
                                                    IconButton(onClick = {
                                                        val temp = stationsState[index]
                                                        stationsState[index] = stationsState[index + 1]
                                                        stationsState[index + 1] = temp
                                                        if (currentIndex == index) currentIndex++ else if (currentIndex == index + 1) currentIndex--
                                                    }, modifier = Modifier.size(32.dp)) { Text("🔽") }
                                                }
                                                
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(onClick = { stationsState[index] = station.copy(isFavorite = !station.isFavorite) }, modifier = Modifier.size(32.dp)) { Text(if (station.isFavorite) "❤️" else "🤍", fontSize = 18.sp) }
                                                IconButton(onClick = {
                                                    if (stationsState.size > 1) {
                                                        val wasPlaying = (currentIndex == index && RadioManager.isPlaying.value)
                                                        stationsState.removeAt(index)
                                                        if (currentIndex >= stationsState.size) currentIndex = stationsState.size - 1
                                                        if (wasPlaying && stationsState.isNotEmpty()) playStation(currentIndex) else if (stationsState.isEmpty()) { RadioManager.pause(); RadioManager.statusText.value = "Kanal kalmadı" }
                                                    }
                                                }, modifier = Modifier.size(32.dp)) { Text("🗑️", fontSize = 18.sp) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        0 -> {}
                    }
                }

                if (activeSettingDialog != null) {
                    Dialog(onDismissRequest = { activeSettingDialog = null }) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            shape = RoundedCornerShape(16.dp),
                            color = CardBg
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = when (activeSettingDialog) {
                                        0 -> "Genel Bilgiler"
                                        1 -> "İnternet Ayarları"
                                        2 -> "Sistem İzinleri"
                                        3 -> "Yeni Kanal Ekle"
                                        else -> "Ayarlar"
                                    },
                                    color = Orange,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 420.dp)
                                ) {
                                    when (activeSettingDialog) {
                                        0 -> {
                                            Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Radyo Uygulaması", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text("Tüm hakları saklıdır. Kesintisiz yayın keyfi!", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
                                            }
                                        }
                                        1 -> {
                                            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                item { OutlinedTextField(value = startThresholdSec, onValueChange = { startThresholdSec = it }, label = { Text("Oynatma Eşiği (Saniye)", color = Color.LightGray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Orange, unfocusedBorderColor = Color.Gray), modifier = Modifier.fillMaxWidth()) }
                                                item { OutlinedTextField(value = minBufferSec, onValueChange = { minBufferSec = it }, label = { Text("Min Buffer (Saniye)", color = Color.LightGray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Orange, unfocusedBorderColor = Color.Gray), modifier = Modifier.fillMaxWidth()) }
                                                item { OutlinedTextField(value = maxBufferSec, onValueChange = { maxBufferSec = it }, label = { Text("Max Buffer (Saniye)", color = Color.LightGray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Orange, unfocusedBorderColor = Color.Gray), modifier = Modifier.fillMaxWidth()) }
                                                item { OutlinedTextField(value = timeoutSec, onValueChange = { timeoutSec = it }, label = { Text("Zaman Aşımı (Saniye)", color = Color.LightGray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Orange, unfocusedBorderColor = Color.Gray), modifier = Modifier.fillMaxWidth()) }
                                                item { OutlinedTextField(value = retryIntervalSec, onValueChange = { retryIntervalSec = it }, label = { Text("Yeniden Bağlanma (Saniye)", color = Color.LightGray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Orange, unfocusedBorderColor = Color.Gray), modifier = Modifier.fillMaxWidth()) }
                                                item {
                                                    Button(onClick = {
                                                        RadioManager.startThresholdSec = startThresholdSec.toFloatOrNull() ?: 1.5f
                                                        RadioManager.minBufferSec = minBufferSec.toIntOrNull() ?: 30
                                                        RadioManager.maxBufferSec = maxBufferSec.toIntOrNull() ?: 120
                                                        RadioManager.timeoutSec = timeoutSec.toIntOrNull() ?: 60
                                                        RadioManager.retryIntervalSec = retryIntervalSec.toLongOrNull() ?: 5L
                                                        RadioManager.createPlayer(context = context)
                                                        saveMessage = "✅ Kaydedildi!"
                                                    }, colors = ButtonDefaults.buttonColors(containerColor = Orange), modifier = Modifier.fillMaxWidth().height(45.dp)) { Text("Ayarları Kaydet", color = Color.White, fontWeight = FontWeight.Bold) }
                                                    if (saveMessage.isNotEmpty()) { Spacer(modifier = Modifier.height(4.dp)); Text(saveMessage, color = Color.Green, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                                                }
                                            }
                                        }
                                        2 -> {
                                            val lifecycleOwner = LocalLifecycleOwner.current
                                            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                                            var hasNotificationPerm by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true) }
                                            var isBatteryOptimized by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }
                                            var isAutoStartClicked by remember { mutableStateOf(false) }
                                            val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasNotificationPerm = it }

                                            DisposableEffect(lifecycleOwner) {
                                                val observer = LifecycleEventObserver { _, event ->
                                                    if (event == Lifecycle.Event.ON_RESUME) {
                                                        isBatteryOptimized = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) hasNotificationPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                                    }
                                                }
                                                lifecycleOwner.lifecycle.addObserver(observer)
                                                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                                            }

                                            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                item { Card(colors = CardDefaults.cardColors(containerColor = DarkBg), modifier = Modifier.fillMaxWidth().clickable { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPerm) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (hasNotificationPerm) "✅" else "❌", fontSize = 22.sp); Spacer(modifier = Modifier.width(12.dp)); Column { Text("Bildirim İzni", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("Çalıştığını gösterir.", color = Color.LightGray, fontSize = 11.sp) } } } }
                                                item { Card(colors = CardDefaults.cardColors(containerColor = DarkBg), modifier = Modifier.fillMaxWidth().clickable { if (!isBatteryOptimized) { try { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }) } catch(e: Exception) {} } }) { Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (isBatteryOptimized) "✅" else "❌", fontSize = 22.sp); Spacer(modifier = Modifier.width(12.dp)); Column { Text("Pil Kısıtlaması (Sınırsız)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("Ekran kapalıyken canlı tutar.", color = Color.LightGray, fontSize = 11.sp) } } } }
                                                item { Card(colors = CardDefaults.cardColors(containerColor = DarkBg), modifier = Modifier.fillMaxWidth().clickable { isAutoStartClicked = true; try { context.startActivity(Intent().apply { component = android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity") }) } catch(e: Exception) { try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) } catch(ex: Exception) {} } }) { Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (isAutoStartClicked) "✅" else "⚠️", fontSize = 22.sp); Spacer(modifier = Modifier.width(12.dp)); Column { Text("Otomatik Başlatma (Xiaomi)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("Tıklayıp açın.", color = Color.LightGray, fontSize = 11.sp) } } } }
                                            }
                                        }
                                        3 -> {
                                            var newName by remember { mutableStateOf("") }
                                            var newUrl by remember { mutableStateOf("") }
                                            var newGenre by remember { mutableStateOf("") }
                                            var addMsg by remember { mutableStateOf("") }

                                            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                item { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Kanal Adı (Zorunlu)", color = Color.LightGray) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Orange, unfocusedBorderColor = Color.Gray), modifier = Modifier.fillMaxWidth()) }
                                                item { OutlinedTextField(value = newUrl, onValueChange = { newUrl = it }, label = { Text("Yayın Linki - URL (Zorunlu)", color = Color.LightGray) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Orange, unfocusedBorderColor = Color.Gray), modifier = Modifier.fillMaxWidth()) }
                                                item { OutlinedTextField(value = newGenre, onValueChange = { newGenre = it }, label = { Text("Tür (Örn: Pop, Haber)", color = Color.LightGray) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Orange, unfocusedBorderColor = Color.Gray), modifier = Modifier.fillMaxWidth()) }
                                                item {
                                                    Button(onClick = {
                                                        if (newName.isNotBlank() && newUrl.isNotBlank()) {
                                                            stationsState.add(0, RadioStation(newName, newGenre, newUrl, false))
                                                            if (stationsState.size > 1) currentIndex++ else currentIndex = 0
                                                            addMsg = "✅ Kanal başarıyla eklendi!"
                                                            newName = ""; newUrl = ""; newGenre = ""
                                                        } else { addMsg = "⚠️ Ad ve URL zorunludur." }
                                                    }, colors = ButtonDefaults.buttonColors(containerColor = Orange), modifier = Modifier.fillMaxWidth().height(45.dp)) { Text("Kanalı Ekle", color = Color.White, fontWeight = FontWeight.Bold) }
                                                    if (addMsg.isNotEmpty()) { Spacer(modifier = Modifier.height(4.dp)); Text(addMsg, color = if(addMsg.contains("✅")) Color.Green else Color.Yellow, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                                                }
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = { activeSettingDialog = null },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                    modifier = Modifier.fillMaxWidth().height(45.dp)
                                ) {
                                    Text("Pencereyi Kapat", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                val currentStationName = RadioManager.currentStationName.value.ifEmpty { "Radyo Hazır" }
                val statusText = RadioManager.statusText.value
                val isPlaying = RadioManager.isPlaying.value

                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .background(BottomBarBg, shape = RoundedCornerShape(16.dp))
                    .padding(16.dp), 
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(currentStationName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(statusText, color = Orange, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { RadioManager.onPrevAction?.invoke() }, colors = ButtonDefaults.buttonColors(containerColor = Orange)) { Text("<<") }
                        Button(onClick = {
                            if (isPlaying) {
                                RadioManager.pause()
                                val stopIntent = Intent(context, RadioService::class.java).setAction("ACTION_STOP")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(stopIntent)
                                } else {
                                    context.startService(stopIntent)
                                }
                            } else {
                                if (RadioManager.player?.mediaItemCount == 0 && stationsState.isNotEmpty()) {
                                    playStation(currentIndex)
                                } else {
                                    RadioManager.resume()
                                    val playIntent = Intent(context, RadioService::class.java).setAction("ACTION_PLAY")
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(playIntent)
                                    } else {
                                        context.startService(playIntent)
                                    }
                                }
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Orange)) { Text(if (isPlaying) "|| Duraklat" else "► Oynat") }
                        Button(onClick = { RadioManager.onNextAction?.invoke() }, colors = ButtonDefaults.buttonColors(containerColor = Orange)) { Text(">>") }
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth().height(90.dp).background(Color(0xFF202020)), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    val tabs = listOf(
                        Pair("⚙️", "Ayarlar"),
                        Pair("📻", "Kanallar"),
                        Pair("❤️", "Favoriler")
                    )
                    tabs.forEachIndexed { index, (icon, title) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (selectedTab == index) TabActiveBg else Color.Transparent)
                                .clickable { selectedTab = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = icon, fontSize = 20.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = title,
                                    color = if (selectedTab == index) Orange else Color.White,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
