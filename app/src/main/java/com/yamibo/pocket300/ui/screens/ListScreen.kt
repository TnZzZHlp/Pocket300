package com.yamibo.pocket300.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yamibo.pocket300.R
import com.yamibo.pocket300.ui.ScreenScaffold

@Composable
internal fun ListScreen() {
    ScreenScaffold(stringResource(R.string.list_title)) { padding ->
        Spacer(Modifier.fillMaxSize().padding(padding))
    }
}
