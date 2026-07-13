package com.yamibo.pocket300.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.ui.AppColorTheme
import com.yamibo.pocket300.ui.ReaderPreferencesStore
import com.yamibo.pocket300.ui.ScreenScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    colorTheme: AppColorTheme,
    onColorThemeChange: (AppColorTheme) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val preferencesStore = remember(context) { ReaderPreferencesStore(context) }
    var preferences by remember { mutableStateOf(preferencesStore.load()) }

    ScreenScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(top = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_app_theme),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppColorTheme.entries.forEach { theme ->
                            FilterChip(
                                selected = colorTheme == theme,
                                onClick = { onColorThemeChange(theme) },
                                label = { Text(stringResource(theme.labelResource)) },
                                leadingIcon = {
                                    Surface(
                                        modifier = Modifier.size(16.dp),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        color = theme.previewColor,
                                    ) {}
                                },
                            )
                        }
                    }
                }
                Surface(Modifier.fillMaxWidth()) {
                    ReaderSettingsSheet(preferences) { updated ->
                        preferences = updated
                        preferencesStore.save(updated)
                    }
                }
            }
        }
    }
}

private val AppColorTheme.previewColor: Color
    get() = when (this) {
        AppColorTheme.SYSTEM -> Color(0xFF777777)
        AppColorTheme.SAKURA -> Color(0xFF8F4A60)
        AppColorTheme.VIOLET -> Color(0xFF6750A4)
        AppColorTheme.BLUE -> Color(0xFF0061A4)
        AppColorTheme.GREEN -> Color(0xFF006C4C)
    }

private val AppColorTheme.labelResource: Int
    get() = when (this) {
        AppColorTheme.SYSTEM -> R.string.settings_theme_system
        AppColorTheme.SAKURA -> R.string.settings_theme_sakura
        AppColorTheme.VIOLET -> R.string.settings_theme_violet
        AppColorTheme.BLUE -> R.string.settings_theme_blue
        AppColorTheme.GREEN -> R.string.settings_theme_green
    }
