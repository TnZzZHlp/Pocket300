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
import com.yamibo.pocket300.ui.AppColorTheme

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

private val VioletLightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF625B71),
    secondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFF7D5260),
)

private val VioletDarkColors = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCBC2DB),
    secondaryContainer = Color(0xFF4A4458),
    tertiary = Color(0xFFEFB8C8),
)

private val BlueLightColors = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    secondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFF6B5778),
)

private val BlueDarkColors = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    secondaryContainer = Color(0xFF3B4858),
    tertiary = Color(0xFFD7BDE4),
)

private val GreenLightColors = lightColorScheme(
    primary = Color(0xFF006C4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF89F8C7),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4D6358),
    secondaryContainer = Color(0xFFCFE9DA),
    tertiary = Color(0xFF3D6373),
)

private val GreenDarkColors = darkColorScheme(
    primary = Color(0xFF6DDBAC),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFF89F8C7),
    secondary = Color(0xFFB3CCBE),
    secondaryContainer = Color(0xFF354B40),
    tertiary = Color(0xFFA5CDDF),
)

@Composable
internal fun PocketTheme(colorTheme: AppColorTheme = AppColorTheme.SYSTEM, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when (colorTheme) {
        AppColorTheme.SYSTEM -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
            dark -> DarkColors
            else -> LightColors
        }
        AppColorTheme.SAKURA -> if (dark) DarkColors else LightColors
        AppColorTheme.VIOLET -> if (dark) VioletDarkColors else VioletLightColors
        AppColorTheme.BLUE -> if (dark) BlueDarkColors else BlueLightColors
        AppColorTheme.GREEN -> if (dark) GreenDarkColors else GreenLightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
