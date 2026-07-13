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
    primary = Color(0xFF79552E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF7DDB5),
    onPrimaryContainer = Color(0xFF2B1907),
    secondary = Color(0xFF705C46),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E0CA),
    onSecondaryContainer = Color(0xFF28190C),
    tertiary = Color(0xFF8B4F3F),
    tertiaryContainer = Color(0xFFFFDAD0),
    background = Color(0xFFFFF8F1),
    onBackground = Color(0xFF211A14),
    surface = Color(0xFFFFF8F1),
    onSurface = Color(0xFF211A14),
    surfaceVariant = Color(0xFFEFE1D3),
    onSurfaceVariant = Color(0xFF514539),
    outline = Color(0xFF837568),
    outlineVariant = Color(0xFFD6C4B4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF3E7),
    surfaceContainer = Color(0xFFF9EDE1),
    surfaceContainerHigh = Color(0xFFF3E7DB),
    surfaceContainerHighest = Color(0xFFEDDFD2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE9BD82),
    onPrimary = Color(0xFF432C0D),
    primaryContainer = Color(0xFF5C421F),
    onPrimaryContainer = Color(0xFFFFDDAE),
    secondary = Color(0xFFD6C2AA),
    onSecondary = Color(0xFF3B2F22),
    secondaryContainer = Color(0xFF524638),
    onSecondaryContainer = Color(0xFFF3DFC6),
    tertiary = Color(0xFFEDB8A7),
    tertiaryContainer = Color(0xFF69382C),
    background = Color(0xFF1F1B17),
    onBackground = Color(0xFFEAE1D8),
    surface = Color(0xFF1F1B17),
    onSurface = Color(0xFFEAE1D8),
    surfaceVariant = Color(0xFF51463B),
    onSurfaceVariant = Color(0xFFD3C4B5),
    outline = Color(0xFF9C8F82),
    outlineVariant = Color(0xFF51463B),
    surfaceContainerLowest = Color(0xFF191511),
    surfaceContainerLow = Color(0xFF27211C),
    surfaceContainer = Color(0xFF2B251F),
    surfaceContainerHigh = Color(0xFF362F28),
    surfaceContainerHighest = Color(0xFF413A32),
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
internal fun PocketTheme(colorTheme: AppColorTheme = AppColorTheme.BEIGE, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when (colorTheme) {
        AppColorTheme.SYSTEM -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
            dark -> DarkColors
            else -> LightColors
        }
        AppColorTheme.BEIGE -> if (dark) DarkColors else LightColors
        AppColorTheme.VIOLET -> if (dark) VioletDarkColors else VioletLightColors
        AppColorTheme.BLUE -> if (dark) BlueDarkColors else BlueLightColors
        AppColorTheme.GREEN -> if (dark) GreenDarkColors else GreenLightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
