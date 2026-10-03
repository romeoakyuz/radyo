package com.example.xiaomi_radyo

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

data class SavedStation(
    val id: String,
    val name: String,
    val genre: String,
    val streamUrl: String
)

object RadioStateHolder {
    val statusText = MutableStateFlow("Duraklatıldı")
    val isPlaying = MutableStateFlow(false)
    val currentStationName = MutableStateFlow("Kral FM")
    val currentStreamUrl = MutableStateFlow("https://ssldyg.radyotvonline.com/smil/smil:kralfm.smil/playlist.m3u8")

    private val defaultStations = listOf(
        SavedStation("1", "Kral FM", "Damar & Arabesk", "https://ssldyg.radyotvonline.com/smil/smil:kralfm.smil/playlist.m3u8"),
        SavedStation("2", "Alem FM", "Pop & Canlı Müzik", "https://alemfm.radyotvonline.net/alemfmaac"),
        SavedStation("3", "Best FM", "Ulusal / Pop", "https://ssldyg.radyotvonline.com/best/bestfm.stream/playlist.m3u8")
    )

    fun getSavedStations(context: Context): List<SavedStation> {
        val prefs = context.getSharedPreferences("xiaomi_radyo_prefs", Context.MODE_PRIVATE)
        val serialized = prefs.getString("user_stations_data", null)
        if (serialized.isNullOrEmpty()) return defaultStations
        return try {
            val list = mutableListOf<SavedStation>()
            val lines = serialized.split(";;;")
            for (line in lines) {
                val parts = line.split("|||")
                if (parts.size >= 4) {
                    list.add(SavedStation(parts[0], parts[1], parts[2], parts[3]))
                }
            }
            if (list.isNotEmpty()) list else defaultStations
        } catch (e: Exception) {
            defaultStations
        }
    }

    fun saveStations(context: Context, stations: List<SavedStation>) {
        val serialized = stations.joinToString(";;;") { "${it.id}|||${it.name}|||${it.genre}|||${it.streamUrl}" }
        val prefs = context.getSharedPreferences("xiaomi_radyo_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("user_stations_data", serialized).apply()
    }
    
    fun addCustomStation(context: Context, name: String, genre: String, streamUrl: String) {
        val currentList = getSavedStations(context).toMutableList()
        val newId = (currentList.size + 1).toString()
        currentList.add(SavedStation(newId, name, genre, streamUrl))
        saveStations(context, currentList)
    }

    fun nextStation(context: Context) {
        val stations = getSavedStations(context)
        if (stations.isEmpty()) return
        val currentIndex = stations.indexOfFirst { it.streamUrl == currentStreamUrl.value }
        val nextIndex = if (currentIndex != -1 && currentIndex < stations.size - 1) currentIndex + 1 else 0
        val target = stations[nextIndex]
        currentStationName.value = target.name
        currentStreamUrl.value = target.streamUrl
    }

    fun prevStation(context: Context) {
        val stations = getSavedStations(context)
        if (stations.isEmpty()) return
        val currentIndex = stations.indexOfFirst { it.streamUrl == currentStreamUrl.value }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else stations.size - 1
        val target = stations[prevIndex]
        currentStationName.value = target.name
        currentStreamUrl.value = target.streamUrl
    }
}
