package com.example.xiaomi_radyo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private val notifPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        updateUI()
        if(granted) Toast.makeText(this, "Bildirim izni verildi ✓", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<android.view.View>(R.id.card_notif).setOnClickListener { requestNotif() }
        findViewById<android.view.View>(R.id.card_battery).setOnClickListener { requestBattery() }
        findViewById<android.view.View>(R.id.card_lockscreen).setOnClickListener { openNotifSettings() }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI(){
        // 1 - Bildirim
        val iconNotif = findViewById<TextView>(R.id.icon_notif)
        val hasNotif = if(Build.VERSION.SDK_INT >= 33){
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        if(hasNotif){
            iconNotif.text = "✓"
            iconNotif.setTextColor(0xFF4CAF50.toInt())
            iconNotif.setBackgroundColor(0x334CAF50)
        } else {
            iconNotif.text = "✕"
            iconNotif.setTextColor(0xFFFF4444.toInt())
            iconNotif.setBackgroundColor(0x33FF4444)
        }

        // 2 - Pil Optimizasyonu
        val iconBattery = findViewById<TextView>(R.id.icon_battery)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)
        if(isIgnoring){
            iconBattery.text = "✓"
            iconBattery.setTextColor(0xFF4CAF50.toInt())
            iconBattery.setBackgroundColor(0x334CAF50)
        } else {
            iconBattery.text = "✕"
            iconBattery.setTextColor(0xFFFF4444.toInt())
            iconBattery.setBackgroundColor(0x33FF4444)
        }
    }

    private fun requestNotif(){
        if(Build.VERSION.SDK_INT >= 33){
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotifSettings()
        }
    }

    private fun requestBattery(){
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception){
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun openNotifSettings(){
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        } catch (e: Exception){
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }
}
