package com.yamibo.pocket300.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.yamibo.pocket300.ui.AppColorTheme

internal val LightColors = lightColorScheme(
    primary = Color(0xFF755338),
    surfaceTint = Color(0xFF755338),
    inverseSurface = Color(0xFF302E29),
    inverseOnSurface = Color(0xFFFAF8F3),
    inversePrimary = Color(0xFFD9B58B),
    onPrimary = Color(0xFFFFFAF3),
    primaryContainer = Color(0xFFEEE4D5),
    onPrimaryContainer = Color(0xFF513923),
    secondary = Color(0xFF6B6052),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE5D9),
    onSecondaryContainer = Color(0xFF453D32),
    tertiary = Color(0xFF78584C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0E2DA),
    onTertiaryContainer = Color(0xFF54382E),
    background = Color(0xFFFAF8F3),
    onBackground = Color(0xFF302E29),
    surface = Color(0xFFFAF8F3),
    onSurface = Color(0xFF302E29),
    surfaceVariant = Color(0xFFF1EDE5),
    onSurfaceVariant = Color(0xFF6B655B),
    outline = Color(0xFF81796E),
    outlineVariant = Color(0xFFE4DED4),
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFFFFDF9),
    surfaceContainer = Color(0xFFF5F1EA),
    surfaceContainerHigh = Color(0xFFF1EDE5),
    surfaceContainerHighest = Color(0xFFEAE4DA),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFD9B58B),
    surfaceTint = Color(0xFFD9B58B),
    inverseSurface = Color(0xFFE8E1D5),
    inverseOnSurface = Color(0xFF302E29),
    inversePrimary = Color(0xFF755338),
    onPrimary = Color(0xFF2C2116),
    primaryContainer = Color(0xFF44372A),
    onPrimaryContainer = Color(0xFFF0DCC3),
    secondary = Color(0xFFD0C2AF),
    onSecondary = Color(0xFF302A22),
    secondaryContainer = Color(0xFF3E372D),
    onSecondaryContainer = Color(0xFFE7DCCD),
    tertiary = Color(0xFFDABAA9),
    onTertiary = Color(0xFF38271F),
    tertiaryContainer = Color(0xFF48362D),
    onTertiaryContainer = Color(0xFFF1DED2),
    background = Color(0xFF211F1B),
    onBackground = Color(0xFFE8E1D5),
    surface = Color(0xFF211F1B),
    onSurface = Color(0xFFE8E1D5),
    surfaceVariant = Color(0xFF332F28),
    onSurfaceVariant = Color(0xFFB8AFA2),
    outline = Color(0xFF978D7F),
    outlineVariant = Color(0xFF484036),
    surfaceContainerLowest = Color(0xFF1B1916),
    surfaceContainerLow = Color(0xFF292620),
    surfaceContainer = Color(0xFF2E2A24),
    surfaceContainerHigh = Color(0xFF332F28),
    surfaceContainerHighest = Color(0xFF3B352D),
)

private val VioletLightColors = LightColors.copy(
    primary = Color(0xFF6750A4),
    surfaceTint = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF625B71),
    secondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFF7D5260),
)

private val VioletDarkColors = DarkColors.copy(
    primary = Color(0xFFCFBCFF),
    surfaceTint = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCBC2DB),
    secondaryContainer = Color(0xFF4A4458),
    tertiary = Color(0xFFEFB8C8),
)

private val BlueLightColors = LightColors.copy(
    primary = Color(0xFF0061A4),
    surfaceTint = Color(0xFF0061A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    secondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFF6B5778),
)

private val BlueDarkColors = DarkColors.copy(
    primary = Color(0xFF9ECAFF),
    surfaceTint = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    secondaryContainer = Color(0xFF3B4858),
    tertiary = Color(0xFFD7BDE4),
)

private val GreenLightColors = LightColors.copy(
    primary = Color(0xFF006C4C),
    surfaceTint = Color(0xFF006C4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF89F8C7),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4D6358),
    secondaryContainer = Color(0xFFCFE9DA),
    tertiary = Color(0xFF3D6373),
)

private val GreenDarkColors = DarkColors.copy(
    primary = Color(0xFF6DDBAC),
    surfaceTint = Color(0xFF6DDBAC),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFF89F8C7),
    secondary = Color(0xFFB3CCBE),
    secondaryContainer = Color(0xFF354B40),
    tertiary = Color(0xFFA5CDDF),
)

internal fun threadTitleColor(colors: ColorScheme, isRead: Boolean): Color =
    if (isRead) colors.onSurfaceVariant else colors.onSurface

internal fun staticColorScheme(colorTheme: AppColorTheme, dark: Boolean): ColorScheme = when (colorTheme) {
    AppColorTheme.SYSTEM, AppColorTheme.BEIGE -> if (dark) DarkColors else LightColors
    AppColorTheme.VIOLET -> if (dark) VioletDarkColors else VioletLightColors
    AppColorTheme.BLUE -> if (dark) BlueDarkColors else BlueLightColors
    AppColorTheme.GREEN -> if (dark) GreenDarkColors else GreenLightColors
}

@Composable
internal fun PocketTheme(colorTheme: AppColorTheme = AppColorTheme.BEIGE, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when (colorTheme) {
        AppColorTheme.SYSTEM -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
            else -> staticColorScheme(colorTheme, dark)
        }
        else -> staticColorScheme(colorTheme, dark)
    }
    MaterialTheme(
        colorScheme = colors,
        typography = PocketTypography,
        shapes = PocketShapes,
        content = content,
    )
}
