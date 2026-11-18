package com.yamibo.pocket300.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

internal object PocketSpacing {
    val screen = 16.dp
    val card = 16.dp
    val itemGap = 10.dp
    val contentGap = 8.dp
    val section = 24.dp
}

@Composable
internal fun pocketCardColors(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) = CardDefaults.cardColors(containerColor = containerColor)

@Composable
internal fun pocketCardBorder(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
