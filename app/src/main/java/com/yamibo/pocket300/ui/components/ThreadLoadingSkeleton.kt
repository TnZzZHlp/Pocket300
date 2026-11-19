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
internal fun ThreadLoadingSkeleton() {
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    val divider = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        Modifier
            .fillMaxSize()
            .padding(horizontal = PocketSpacing.screen)
            .clipToBounds()
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        fun bar(y: Int, fraction: Float, height: Int = 10, x: Int = 0) {
            val start = x.dp.toPx().coerceAtMost(size.width)
            drawRoundRect(
                color = placeholder,
                topLeft = Offset(start, y.dp.toPx()),
                size = Size((size.width - start) * fraction, height.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }

        bar(y = 28, fraction = 0.86f, height = 20)
        bar(y = 58, fraction = 0.52f, height = 20)
        drawCircle(placeholder, radius = 16.dp.toPx(), center = Offset(16.dp.toPx(), 110.dp.toPx()))
        bar(y = 99, fraction = 0.28f, x = 40)
        bar(y = 117, fraction = 0.46f, x = 40)

        fun post(top: Int, lastLineWidth: Float) {
            val dividerY = top.dp.toPx()
            drawLine(divider, Offset(0f, dividerY), Offset(size.width, dividerY))
            drawCircle(
                placeholder,
                radius = 18.dp.toPx(),
                center = Offset(18.dp.toPx(), (top + 37).dp.toPx()),
            )
            bar(y = top + 25, fraction = 0.26f, x = 46)
            bar(y = top + 43, fraction = 0.37f, x = 46)
            bar(y = top + 76, fraction = 0.98f, height = 12)
            bar(y = top + 101, fraction = 0.91f, height = 12)
            bar(y = top + 126, fraction = lastLineWidth, height = 12)
        }

        post(top = 158, lastLineWidth = 0.64f)
        post(top = 328, lastLineWidth = 0.48f)
    }
}
