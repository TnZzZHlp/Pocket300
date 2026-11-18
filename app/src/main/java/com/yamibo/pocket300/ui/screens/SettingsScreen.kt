package com.yamibo.pocket300.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.sp
import com.yamibo.pocket300.BuildConfig
import com.yamibo.pocket300.R
import com.yamibo.pocket300.ui.AppColorTheme
import com.yamibo.pocket300.ui.ReaderPreferencesStore
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.theme.PocketSpacing
import com.yamibo.pocket300.ui.theme.pocketCardBorder
import com.yamibo.pocket300.ui.theme.pocketCardColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    colorTheme: AppColorTheme,
    onColorThemeChange: (AppColorTheme) -> Unit,
    onLogs: () -> Unit,
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PocketSpacing.screen, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = pocketCardColors(),
                    border = pocketCardBorder(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_appearance_section),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_app_theme),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                            shape = CircleShape,
                                            color = theme.previewColor,
                                            border = BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                            ),
                                        ) {}
                                    },
                                )
                            }
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = pocketCardColors(),
                    border = pocketCardBorder(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.reader_settings),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        ReaderTheme(preferences.tone) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.background,
                                contentColor = MaterialTheme.colorScheme.onBackground,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                ),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_reader_preview_text),
                                    modifier = Modifier.padding(14.dp),
                                    fontSize = preferences.fontSizeSp.sp,
                                    lineHeight = (preferences.fontSizeSp * preferences.lineHeightMultiplier).sp,
                                )
                            }
                        }
                        ReaderSettingsControls(
                            preferences = preferences,
                            onChange = { updated ->
                                preferences = updated
                                preferencesStore.save(updated)
                            },
                        )
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = pocketCardColors(),
                    border = pocketCardBorder(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.settings_diagnostics_section),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(
                                start = 18.dp,
                                top = 16.dp,
                                end = 18.dp,
                                bottom = 4.dp,
                            ),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onLogs)
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                Icons.Rounded.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.logs_title),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(R.string.settings_logs_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.settings_version_summary,
                                        BuildConfig.VERSION_NAME,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val AppColorTheme.previewColor: Color
    get() = when (this) {
        AppColorTheme.SYSTEM -> Color(0xFF777777)
        AppColorTheme.BEIGE -> Color(0xFF79552E)
        AppColorTheme.VIOLET -> Color(0xFF6750A4)
        AppColorTheme.BLUE -> Color(0xFF0061A4)
        AppColorTheme.GREEN -> Color(0xFF006C4C)
    }

private val AppColorTheme.labelResource: Int
    get() = when (this) {
        AppColorTheme.SYSTEM -> R.string.settings_theme_system
        AppColorTheme.BEIGE -> R.string.settings_theme_beige
        AppColorTheme.VIOLET -> R.string.settings_theme_violet
        AppColorTheme.BLUE -> R.string.settings_theme_blue
        AppColorTheme.GREEN -> R.string.settings_theme_green
    }
