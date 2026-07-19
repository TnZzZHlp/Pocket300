package com.yamibo.pocket300.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun navigationContentBottomPadding(isTopLevel: Boolean, navigationBarPadding: Dp): Dp =
    if (isTopLevel) navigationBarPadding else 0.dp
