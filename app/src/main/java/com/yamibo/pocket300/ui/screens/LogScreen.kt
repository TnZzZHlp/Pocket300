package com.yamibo.pocket300.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.logging.APP_LOG_CAPACITY
import com.yamibo.pocket300.logging.AppLogEntry
import com.yamibo.pocket300.logging.AppLogger
import com.yamibo.pocket300.logging.LogLevel
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.ScreenScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val logTimestampFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("MM-dd HH:mm:ss.SSS", Locale.ROOT)
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val entries by AppLogger.entries.collectAsState()
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val filteredEntries = remember(entries, selectedLevel) {
        entries
            .asReversed()
            .filter { selectedLevel == null || it.level == selectedLevel }
    }

    ScreenScaffold(
        title = stringResource(R.string.logs_title),
        onBack = onBack,
        actions = {
            IconButton(
                onClick = { shareLogs(context, entries) },
                enabled = entries.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.Share, stringResource(R.string.logs_share))
            }
            IconButton(
                onClick = { showClearConfirmation = true },
                enabled = entries.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.logs_clear))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LogLevelFilterChip(
                        label = stringResource(R.string.logs_level_all),
                        selected = selectedLevel == null,
                        onClick = { selectedLevel = null },
                    )
                    LogLevel.entries.forEach { level ->
                        LogLevelFilterChip(
                            label = stringResource(level.labelResource),
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level },
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.logs_retention_description,
                        APP_LOG_CAPACITY,
                        filteredEntries.size,
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (filteredEntries.isEmpty()) {
                    EmptyState(
                        title = stringResource(
                            if (entries.isEmpty()) R.string.logs_empty_title
                            else R.string.logs_filter_empty_title,
                        ),
                        message = stringResource(
                            if (entries.isEmpty()) R.string.logs_empty_message
                            else R.string.logs_filter_empty_message,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredEntries, key = AppLogEntry::id) { entry ->
                            LogEntryCard(entry)
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.logs_clear_confirmation_title)) },
            text = { Text(stringResource(R.string.logs_clear_confirmation_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        AppLogger.clear()
                        showClearConfirmation = false
                    },
                ) {
                    Text(stringResource(R.string.logs_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.logs_cancel))
                }
            },
        )
    }
}

@Composable
private fun LogLevelFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun LogEntryCard(entry: AppLogEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = logTimestampFormatter.format(Instant.ofEpochMilli(entry.timestampMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = entry.level.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = entry.level.color,
                    )
                    Text(
                        text = "[${entry.component}]",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                if (entry.stackTrace != null) {
                    Text(
                        text = entry.stackTrace,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun shareLogs(context: Context, entries: List<AppLogEntry>) {
    val content = entries.joinToString(separator = "\n\n", transform = ::formatLogEntry)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.logs_share_subject))
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.logs_share)),
    )
}

internal fun formatLogEntry(entry: AppLogEntry): String = buildString {
    append(logTimestampFormatter.format(Instant.ofEpochMilli(entry.timestampMillis)))
    append(' ')
    append(entry.level.name)
    append(" [")
    append(entry.component)
    append("] ")
    append(entry.message)
    entry.stackTrace?.let {
        append('\n')
        append(it)
    }
}

private val LogLevel.labelResource: Int
    get() = when (this) {
        LogLevel.VERBOSE -> R.string.logs_level_verbose
        LogLevel.DEBUG -> R.string.logs_level_debug
        LogLevel.INFO -> R.string.logs_level_info
        LogLevel.WARN -> R.string.logs_level_warn
        LogLevel.ERROR -> R.string.logs_level_error
    }

private val LogLevel.color: Color
    @Composable get() = when (this) {
        LogLevel.VERBOSE -> MaterialTheme.colorScheme.onSurfaceVariant
        LogLevel.DEBUG -> MaterialTheme.colorScheme.secondary
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
