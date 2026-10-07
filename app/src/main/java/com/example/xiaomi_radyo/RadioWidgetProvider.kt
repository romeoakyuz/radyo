package com.example.xiaomi_radyo

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

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.radio_widget)

            val stationName = RadioStateHolder.currentStationName.value
            val statusText = RadioStateHolder.statusText.value
            val isPlaying = RadioStateHolder.isPlaying.value

            views.setTextViewText(R.id.widget_station, stationName)
            views.setTextViewText(R.id.widget_status, statusText)
            
            val playPauseIcon = if (isPlaying) R.drawable.ic_custom_pause else R.drawable.ic_custom_play
            views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

            val prevIntent = Intent(context, RadioService::class.java).apply { action = "WIDGET_PREV" }
            val pendingPrev = PendingIntent.getService(context, 10, prevIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_btn_prev, pendingPrev)

            val toggleIntent = Intent(context, RadioService::class.java).apply { action = "WIDGET_TOGGLE" }
            val pendingToggle = PendingIntent.getService(context, 11, toggleIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, pendingToggle)

            val nextIntent = Intent(context, RadioService::class.java).apply { action = "WIDGET_NEXT" }
            val pendingNext = PendingIntent.getService(context, 12, nextIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_btn_next, pendingNext)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, RadioWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}
