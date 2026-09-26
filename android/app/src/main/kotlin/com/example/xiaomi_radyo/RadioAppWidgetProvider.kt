package com.example.xiaomi_radyo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import es.antonborri.home_widget.HomeWidgetProvider
import es.antonborri.home_widget.HomeWidgetLaunchIntent

/**
 * Xiaomi MIUI / HyperOS 4x1 Radyo Widget Sağlayıcısı
 * Büyük ve dokunması kolay Play/Pause & Next tuşlarını yönetir
 */
class RadioAppWidgetProvider : HomeWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        widgetData: android.content.SharedPreferences
    ) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_radio_4x1).apply {
                // Kanal Adı ve Frekansı
                val stationName = widgetData.getString("widget_station_name", "Kral FM")
                val stationFreq = widgetData.getString("widget_station_freq", "92.0 MHz")
                val isPlaying = widgetData.getBoolean("widget_is_playing", false)

                setTextViewText(R.id.widget_title, stationName)
                setTextViewText(R.id.widget_subtitle, "$stationFreq • Canlı")

                // Play / Pause simgesi
                val playIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                setImageViewResource(R.id.widget_button_play_pause, playIcon)

                // Büyük Play/Pause Tıklama İşlemi
                val playIntent = HomeWidgetLaunchIntent.getActivity(
                    context,
                    MainActivity::class.java,
                    Uri.parse("radiowidget://toggle_play")
                )
                setOnClickPendingIntent(R.id.widget_button_play_pause, playIntent)

                // Büyük Next (Sonraki Kanal) Tıklama İşlemi
                val nextIntent = HomeWidgetLaunchIntent.getActivity(
                    context,
                    MainActivity::class.java,
                    Uri.parse("radiowidget://play_next")
                )
                setOnClickPendingIntent(R.id.widget_button_next, nextIntent)
                
                // Widget gövdesine tıklayınca uygulamayı aç
                val openIntent = Intent(context, MainActivity::class.java)
                val openPending = PendingIntent.getActivity(
                    context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_root, openPending)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
