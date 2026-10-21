package com.heryerde.radyo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.os.Build

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
            val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (event?.action == KeyEvent.ACTION_DOWN) {
                val actionName = when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "ACTION_PLAY"
                    KeyEvent.KEYCODE_MEDIA_NEXT -> "ACTION_NEXT"
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "ACTION_PREV"
                    else -> null
                }
                
                actionName?.let { act ->
                    val serviceIntent = Intent(context, RadioService::class.java).setAction(act)
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (e: Exception) {
                        // Android arka plan kısıtlamalarına takılırsa normal servis olarak tetikle
                        try {
                            context.startService(serviceIntent)
                        } catch (ex: Exception) {}
                    }
                }
            }
        }
    }
}
