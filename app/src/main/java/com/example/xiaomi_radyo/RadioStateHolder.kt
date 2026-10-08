package com.example.xiaomi_radyo

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

data class SavedStation(
    val id: String,
    val name: String,
    val genre: String,
    val streamUrl: String,
    val isFavorite: Boolean = true
)

object RadioStateHolder {
    val statusText = MutableStateFlow("Duraklatıldı")
    val isPlaying = MutableStateFlow(false)
    val currentStationName = MutableStateFlow("Kral FM")
    val currentStreamUrl = MutableStateFlow("https://ssldyg.radyotvonline.com/smil/smil:kralfm.smil/playlist.m3u8")

    val hasNotificationPermission = MutableStateFlow(false)
    val hasBatteryPermission = MutableStateFlow(false)
    val hasAutoStartPermission = MutableStateFlow(false)

    private val defaultStations = listOf(
        SavedStation("1", "Kral FM", "Damar & Arabesk", "https://ssldyg.radyotvonline.com/smil/smil:kralfm.smil/playlist.m3u8", true),
        SavedStation("2", "Alem FM", "Pop & Canlı Müzik", "http://turkmedya.radyotvonline.com/turkmedya/alemfm.stream/playlist.m3u8", true),
        SavedStation("3", "Best FM", "Ulusal / Pop", "https://ssldyg.radyotvonline.com/best/bestfm.stream/playlist.m3u8", true)
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
                    val isFav = if (parts.size >= 5) parts[4].toBoolean() else true
                    list.add(SavedStation(parts[0], parts[1], parts[2], parts[3], isFav))
                }
            }
            if (list.isNotEmpty()) list else defaultStations
        } catch (e: Exception) {
            defaultStations
        }
    }

    fun saveStations(context: Context, stations: List<SavedStation>) {
        val serialized = stations.joinToString(";;;") { "${it.id}|||${it.name}|||${it.genre}|||${it.streamUrl}|||${it.isFavorite}" }
        val prefs = context.getSharedPreferences("xiaomi_radyo_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("user_stations_data", serialized).apply()
    }
    
    fun addCustomStation(context: Context, name: String, genre: String, streamUrl: String) {
        val currentList = getSavedStations(context).toMutableList()
        val newId = System.currentTimeMillis().toString()
        currentList.add(SavedStation(newId, name, genre, streamUrl, false))
        saveStations(context, currentList)
    }

    fun toggleFavorite(context: Context, id: String) {
        val currentList = getSavedStations(context).map {
            if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it
        }
        saveStations(context, currentList)
    }

    fun deleteStation(context: Context, id: String) {
        val currentList = getSavedStations(context).filter { it.id != id }
        saveStations(context, currentList)
    }

    fun nextStation(context: Context) {
        val favStations = getSavedStations(context).filter { it.isFavorite }
        if (favStations.isEmpty()) return
        val currentIndex = favStations.indexOfFirst { it.streamUrl == currentStreamUrl.value }
        val nextIndex = if (currentIndex != -1 && currentIndex < favStations.size - 1) currentIndex + 1 else 0
        val target = favStations[nextIndex]
        currentStationName.value = target.name
        currentStreamUrl.value = target.streamUrl
    }

    fun prevStation(context: Context) {
        val favStations = getSavedStations(context).filter { it.isFavorite }
        if (favStations.isEmpty()) return
        val currentIndex = favStations.indexOfFirst { it.streamUrl == currentStreamUrl.value }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else favStations.size - 1
        val target = favStations[prevIndex]
        currentStationName.value = target.name
        currentStreamUrl.value = target.streamUrl
    }
}
