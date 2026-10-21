package com.heryerde.radyo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class RadioWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, RadioWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        if (intent.action == "ACTION_STATE_CHANGED") {
            for (id in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, RadioWidgetProvider::class.java).apply {
                action = "ACTION_STATE_CHANGED"
            }
            context.sendBroadcast(intent)
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.radyo_widget)
            
            val stationName = RadioManager.currentStationName.value.ifEmpty { "Canlı Radyo" }
            val isPlaying = RadioManager.isPlaying.value

            views.setTextViewText(R.id.widgetStationName, stationName)
            views.setTextViewText(R.id.widget_status_text, if (isPlaying) "Çalınıyor..."  else "Durduruldu")
            
            val playPauseIcon = if (isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
            views.setImageViewResource(R.id.btn_widget_play, playPauseIcon)
            
            val playPauseAction = if (isPlaying) "ACTION_PAUSE" else "ACTION_PLAY"
            views.setOnClickPendingIntent(R.id.btn_widget_play, getServicePendingIntent(context, playPauseAction, 1))
            views.setOnClickPendingIntent(R.id.btn_widget_next, getServicePendingIntent(context, "ACTION_NEXT", 2))
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getServicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, RadioService::class.java).apply { 
                this.action = action 
            }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            } else {
                PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
        }
    }
}
