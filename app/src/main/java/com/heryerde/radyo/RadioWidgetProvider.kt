package com.heryerde.radyo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

        when (intent.action) {
            "ACTION_WIDGET_PLAY_PAUSE" -> {
                if (RadioManager.isPlaying.value) RadioManager.pause() else RadioManager.resume()
                for (id in appWidgetIds) updateAppWidget(context, appWidgetManager, id)
            }
            "ACTION_WIDGET_NEXT" -> {
                RadioManager.onNextAction?.invoke()
                for (id in appWidgetIds) updateAppWidget(context, appWidgetManager, id)
            }
            "ACTION_WIDGET_PREV" -> {
                RadioManager.onPrevAction?.invoke()
                for (id in appWidgetIds) updateAppWidget(context, appWidgetManager, id)
            }
            "ACTION_STATE_CHANGED" -> {
                for (id in appWidgetIds) updateAppWidget(context, appWidgetManager, id)
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
            val views = RemoteViews(context.packageName, R.layout.widget_radio)
            
            val stationName = RadioManager.currentStationName.value.ifEmpty { "Radyo Hazır" }
            val isPlaying = RadioManager.isPlaying.value

            views.setTextViewText(R.id.widgetStationName, stationName)
            views.setTextViewText(R.id.widgetPlayPauseBtn, if (isPlaying) "||" else "▶")

            views.setOnClickPendingIntent(R.id.widgetPlayPauseBtn, getPendingIntent(context, "ACTION_WIDGET_PLAY_PAUSE"))
            views.setOnClickPendingIntent(R.id.widgetNextBtn, getPendingIntent(context, "ACTION_WIDGET_NEXT"))
            views.setOnClickPendingIntent(R.id.widgetPrevBtn, getPendingIntent(context, "ACTION_WIDGET_PREV"))
            
            // Boşluğa tıklayınca uygulamayı aç
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, RadioWidgetProvider::class.java).apply { this.action = action }
            return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
