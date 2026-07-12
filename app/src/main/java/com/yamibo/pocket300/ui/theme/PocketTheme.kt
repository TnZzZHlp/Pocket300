package com.yamibo.pocket300.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF8F4A60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3B071D),
    secondary = Color(0xFF75565E),
    secondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFF7C5635),
    background = Color(0xFFFFF8F8),
    surface = Color(0xFFFFF8F8),
    surfaceVariant = Color(0xFFF2DDE2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C6),
    onPrimary = Color(0xFF56142F),
    primaryContainer = Color(0xFF713048),
    secondary = Color(0xFFE4BDC6),
    tertiary = Color(0xFFEDBD8D),
    background = Color(0xFF201A1B),
    surface = Color(0xFF201A1B),
    surfaceVariant = Color(0xFF514347),
)

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
