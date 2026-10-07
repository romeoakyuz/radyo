package com.example.xiaomi_radyo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Gece modu (Koyu) için logo renkleri
private val DarkColorScheme = darkColorScheme(
    primary = BrandOrange,
    secondary = BrandOrange,
    tertiary = BrandOrange,
    background = BrandDarkBg,
    surface = BrandDarkSurface,
    surfaceVariant = BrandDarkSurfaceVariant,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White
)

// Gündüz modu (Aydınlık) için turuncu vurgulu sade renkler
private val LightColorScheme = lightColorScheme(
    primary = BrandOrange,
    secondary = BrandOrange,
    tertiary = BrandOrange,
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9ECEF),
    onPrimary = Color.White,
    onBackground = BrandDarkBg,
    onSurface = BrandDarkBg,
    onSurfaceVariant = BrandDarkBg
)

@Composable
fun Xiaomi_radyoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Bildirim çubuğunun rengini uygulamanın arka planına uydurur
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
