package com.heryerde.radyo

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            val serviceIntent = Intent(context, RadioService::class.java).apply {
                // KRİTİK DEĞİŞİKLİK: Artık PLAY göndermiyoruz, sadece UYANDIR komutu veriyoruz.
                action = "ACTION_WAKE_UP" 
            }
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                try {
                    context.startService(serviceIntent)
                } catch (ex: Exception) {}
            }
        }
    }
}
