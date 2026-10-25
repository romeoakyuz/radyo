package com.heryerde.radyo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.os.Build

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            
            // Tıklanan donanım tuşunun bilgisini alıyoruz
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }

            // Eğer tuşa basılma (aşağı inme) işlemiyse:
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                val serviceIntent = Intent(context, RadioService::class.java)
                
                // Gelen tuş komutunu servisimizin anlayacağı dile çeviriyoruz
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> serviceIntent.action = "ACTION_PLAY"
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> serviceIntent.action = "ACTION_PAUSE"
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        // Eğer zaten çalıyorsa durdur, tamamen kapalıysa veya durmuşsa oynat
                        if (RadioManager.isPlaying.value) {
                            serviceIntent.action = "ACTION_PAUSE"
                        } else {
                            serviceIntent.action = "ACTION_PLAY"
                        }
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> serviceIntent.action = "ACTION_NEXT"
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> serviceIntent.action = "ACTION_PREV"
                    KeyEvent.KEYCODE_MEDIA_STOP -> serviceIntent.action = "ACTION_STOP"
                    else -> return // Başka bir donanım tuşuysa görmezden gel
                }

                // Uygulama kapalı olsa bile Android bu duruma izin verir, servisi zorla uyandırırız
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
