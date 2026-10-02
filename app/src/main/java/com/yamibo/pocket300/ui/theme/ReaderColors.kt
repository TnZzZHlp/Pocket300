package com.yamibo.pocket300.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.yamibo.pocket300.ui.ReaderTone

internal fun readerColorScheme(tone: ReaderTone, base: ColorScheme): ColorScheme = when (tone) {
    ReaderTone.SYSTEM -> base
    ReaderTone.PAPER -> LightColors.copy(
        primary = Color(0xFF795548),
        surfaceTint = Color(0xFF795548),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADCC8),
        onPrimaryContainer = Color(0xFF342018),
        background = Color(0xFFF7F0E3),
        onBackground = Color(0xFF322C25),
        surface = Color(0xFFF7F0E3),
        onSurface = Color(0xFF322C25),
        surfaceVariant = Color(0xFFE9E0D2),
        onSurfaceVariant = Color(0xFF655C51),
        surfaceContainerLow = Color(0xFFFFF9EF),
        surfaceContainer = Color(0xFFF2EADB),
        surfaceContainerHigh = Color(0xFFE9E0D2),
        surfaceContainerHighest = Color(0xFFE5DCCD),
        outlineVariant = Color(0xFFDACFBE),
    )
    ReaderTone.MINT -> LightColors.copy(
        primary = Color(0xFF3F6655),
        surfaceTint = Color(0xFF3F6655),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD0E8D8),
        onPrimaryContainer = Color(0xFF163A2B),
        background = Color(0xFFEFF6EE),
        onBackground = Color(0xFF243029),
        surface = Color(0xFFEFF6EE),
        onSurface = Color(0xFF243029),
        surfaceVariant = Color(0xFFDCE9DC),
        onSurfaceVariant = Color(0xFF526158),
        surfaceContainerLow = Color(0xFFF5FAF3),
        surfaceContainer = Color(0xFFE7EFE4),
        surfaceContainerHigh = Color(0xFFDCE9DC),
        surfaceContainerHighest = Color(0xFFD6E3D6),
        outlineVariant = Color(0xFFC8D6C8),
    )
    ReaderTone.NIGHT -> DarkColors.copy(
        primary = Color(0xFFD6B98C),
        surfaceTint = Color(0xFFD6B98C),
        onPrimary = Color(0xFF402D10),
        primaryContainer = Color(0xFF59451F),
        onPrimaryContainer = Color(0xFFF4DCB0),
        background = Color(0xFF171819),
        onBackground = Color(0xFFD7D4CE),
        surface = Color(0xFF171819),
        onSurface = Color(0xFFD7D4CE),
        surfaceVariant = Color(0xFF303234),
        onSurfaceVariant = Color(0xFFB8B6B0),
        surfaceContainerLow = Color(0xFF202224),
        surfaceContainer = Color(0xFF27292B),
        surfaceContainerHigh = Color(0xFF303234),
        surfaceContainerHighest = Color(0xFF37393B),
        outlineVariant = Color(0xFF46484A),
    )
}
