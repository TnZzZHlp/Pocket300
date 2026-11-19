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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.ui.theme.PocketSpacing

@Composable
internal fun ForumLoadingSkeleton() {
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    val divider = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        Modifier
            .fillMaxSize()
            .padding(horizontal = PocketSpacing.screen)
            .clipToBounds()
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        fun bar(y: Int, fraction: Float, height: Int, x: Float = 0f) {
            drawRoundRect(
                color = placeholder,
                topLeft = Offset(size.width * x, y.dp.toPx()),
                size = Size(size.width * fraction, height.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }

        bar(y = 16, fraction = 0.16f, height = 30)
        bar(y = 16, fraction = 0.16f, height = 30, x = 0.19f)
        bar(y = 16, fraction = 0.16f, height = 30, x = 0.38f)
        bar(y = 16, fraction = 0.24f, height = 30, x = 0.76f)

        listOf(0.86f, 0.68f, 0.94f, 0.76f, 0.81f, 0.71f).forEachIndexed { index, width ->
            val top = 62 + index * 114
            val dividerY = top.dp.toPx()
            drawLine(divider, Offset(0f, dividerY), Offset(size.width, dividerY))
            drawCircle(
                color = placeholder,
                radius = 12.dp.toPx(),
                center = Offset(12.dp.toPx(), (top + 24).dp.toPx()),
            )
            bar(y = top + 19, fraction = 0.28f, height = 10, x = 0.16f)
            bar(y = top + 47, fraction = width, height = 16)
            bar(y = top + 81, fraction = 0.25f, height = 10)
        }
    }
}
