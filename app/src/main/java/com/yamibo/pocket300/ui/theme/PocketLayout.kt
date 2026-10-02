package com.yamibo.pocket300.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
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
