package com.yamibo.pocket300.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.ui.theme.PocketSpacing

@Composable
internal fun ForumIndexLoadingSkeleton() {
    ListLoadingCanvas { placeholder, _ ->
        val card = placeholder.copy(alpha = 0.4f)
        repeat(2) { category ->
            val top = 12 + category * 296
            skeletonBar(placeholder, top + 6, 0.3f, 14)
            repeat(3) { row ->
                val rowTop = top + 34 + row * 82
                skeletonBar(card, rowTop, 1f, 72, radius = 12)
                skeletonBar(placeholder, rowTop + 16, 0.46f, 14, x = 0.05f)
                skeletonBar(placeholder, rowTop + 43, 0.66f, 10, x = 0.05f)
            }
        }
    }
}

@Composable
internal fun ThreadListLoadingSkeleton(showFilters: Boolean = false) {
    ListLoadingCanvas { placeholder, divider ->
        if (showFilters) {
            skeletonBar(placeholder, 16, 0.16f, 30)
            skeletonBar(placeholder, 16, 0.16f, 30, x = 0.19f)
            skeletonBar(placeholder, 16, 0.16f, 30, x = 0.38f)
            skeletonBar(placeholder, 16, 0.24f, 30, x = 0.76f)
        }
        val firstRowTop = if (showFilters) 62 else 8
        threadTitleWidths.forEachIndexed { index, width ->
            val top = firstRowTop + index * 114
            val dividerY = top.dp.toPx()
            drawLine(divider, Offset(0f, dividerY), Offset(size.width, dividerY))
            drawCircle(
                color = placeholder,
                radius = 12.dp.toPx(),
                center = Offset(12.dp.toPx(), (top + 24).dp.toPx()),
            )
            skeletonBar(placeholder, top + 19, 0.28f, 10, x = 0.16f)
            skeletonBar(placeholder, top + 47, width, 16)
            skeletonBar(placeholder, top + 81, 0.25f, 10)
        }
    }
}

@Composable
private fun ListLoadingCanvas(draw: DrawScope.(placeholder: Color, divider: Color) -> Unit) {
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    val divider = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        Modifier
            .fillMaxSize()
            .padding(horizontal = PocketSpacing.screen)
            .clipToBounds()
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        draw(placeholder, divider)
    }
}

private fun DrawScope.skeletonBar(
    color: Color,
    y: Int,
    fraction: Float,
    height: Int,
    x: Float = 0f,
    radius: Int = 4,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * x, y.dp.toPx()),
        size = Size(size.width * fraction, height.dp.toPx()),
        cornerRadius = CornerRadius(radius.dp.toPx()),
    )
}

private val threadTitleWidths = floatArrayOf(0.86f, 0.68f, 0.94f, 0.76f, 0.81f, 0.71f)
