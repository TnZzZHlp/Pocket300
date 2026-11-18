package com.yamibo.pocket300.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.YamiboThreadSearchType
import com.yamibo.pocket300.data.CustomListDatabase
import com.yamibo.pocket300.data.DEFAULT_CUSTOM_LIST_AUTO_REFRESH_INTERVAL_HOURS
import com.yamibo.pocket300.data.normalizeCustomListKeywords
import com.yamibo.pocket300.logging.AppLogger
import com.yamibo.pocket300.ui.Loading
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.theme.PocketSpacing
import com.yamibo.pocket300.ui.theme.pocketCardBorder
import com.yamibo.pocket300.ui.theme.pocketCardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomListEditorScreen(
    listId: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val database = remember(context) { CustomListDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val notFoundMessage = stringResource(R.string.custom_list_not_found)
    val nameRequiredMessage = stringResource(R.string.custom_list_name_required)
    val valuesRequiredMessage = stringResource(R.string.custom_list_values_required)
    val invalidUserIdMessage = stringResource(R.string.custom_list_user_ids_invalid)
    val invalidAutoRefreshIntervalMessage = stringResource(
        R.string.custom_list_auto_refresh_interval_invalid,
    )
    val saveFailedMessage = stringResource(R.string.custom_list_save_failed)
    var name by rememberSaveable(listId) { mutableStateOf("") }
    var keywordText by rememberSaveable(listId) { mutableStateOf("") }
    var searchType by rememberSaveable(listId) { mutableStateOf(YamiboThreadSearchType.KEYWORD) }
    var autoRefreshIntervalHours by rememberSaveable(listId) {
        mutableStateOf(DEFAULT_CUSTOM_LIST_AUTO_REFRESH_INTERVAL_HOURS.toString())
    }
    var autoDownloadNewThreads by rememberSaveable(listId) { mutableStateOf(false) }
    var autoDeleteAfterImageReading by rememberSaveable(listId) { mutableStateOf(false) }
    var excludedCount by rememberSaveable(listId) { mutableIntStateOf(0) }
    var loading by remember(listId) { mutableStateOf(listId != null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmClearExclusions by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val parsedKeywords = remember(keywordText) { normalizeCustomListKeywords(keywordText) }
    val parsedInterval = autoRefreshIntervalHours.trim().toIntOrNull()

    LaunchedEffect(listId) {
        if (listId != null) {
            val list = withContext(Dispatchers.IO) { database.getList(listId) }
            if (list == null) {
                error = notFoundMessage
            } else {
                name = list.name
                keywordText = list.keywords.joinToString("\n")
                searchType = list.searchType
                autoRefreshIntervalHours = list.autoRefreshIntervalHours.toString()
                autoDownloadNewThreads = list.autoDownloadNewThreads
                autoDeleteAfterImageReading = list.autoDeleteAfterImageReading
                excludedCount = list.excludedCount
            }
            loading = false
        }
    }

    ScreenScaffold(
        title = stringResource(if (listId == null) R.string.custom_list_create else R.string.custom_list_edit),
        onBack = onBack,
    ) { padding ->
        if (loading) {
            Loading(Modifier.padding(padding))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = PocketSpacing.screen, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = pocketCardColors(),
                        border = pocketCardBorder(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                stringResource(R.string.custom_list_rules_section),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.custom_list_name)) },
                                isError = error == nameRequiredMessage && name.isBlank(),
                                singleLine = true,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(R.string.custom_list_search_type),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    YamiboThreadSearchType.entries.forEach { type ->
                                        FilterChip(
                                            selected = searchType == type,
                                            onClick = { searchType = type },
                                            label = { Text(stringResource(type.labelResource())) },
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = keywordText,
                                onValueChange = { keywordText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.custom_list_search_values)) },
                                isError = error == valuesRequiredMessage || error == invalidUserIdMessage,
                                supportingText = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(searchType.hintResource()))
                                        if (parsedKeywords.isNotEmpty()) {
                                            Text(
                                                stringResource(
                                                    R.string.custom_list_parsed_count,
                                                    parsedKeywords.size,
                                                ),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                                minLines = 4,
                            )
                        }
                    }

                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = pocketCardColors(),
                        border = pocketCardBorder(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.custom_list_automation_section),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(6, 12, 24, 48).forEach { hours ->
                                    FilterChip(
                                        selected = parsedInterval == hours,
                                        onClick = { autoRefreshIntervalHours = hours.toString() },
                                        label = {
                                            Text(
                                                stringResource(
                                                    R.string.custom_list_interval_preset_hours,
                                                    hours,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = autoRefreshIntervalHours,
                                onValueChange = { autoRefreshIntervalHours = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.custom_list_auto_refresh_interval)) },
                                isError = error == invalidAutoRefreshIntervalMessage,
                                supportingText = {
                                    Text(stringResource(R.string.custom_list_auto_refresh_interval_hint))
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                            EditorSwitchTile(
                                title = stringResource(R.string.custom_list_auto_download_new_threads),
                                subtitle = stringResource(R.string.custom_list_auto_download_new_threads_hint),
                                checked = autoDownloadNewThreads,
                                enabled = true,
                                onCheckedChange = { enabled ->
                                    autoDownloadNewThreads = enabled
                                    if (!enabled) autoDeleteAfterImageReading = false
                                },
                            )
                            EditorSwitchTile(
                                title = stringResource(R.string.custom_list_auto_delete_after_image_reading),
                                subtitle = stringResource(R.string.custom_list_auto_delete_after_image_reading_hint),
                                checked = autoDeleteAfterImageReading,
                                enabled = autoDownloadNewThreads,
                                onCheckedChange = { autoDeleteAfterImageReading = it },
                            )
                        }
                    }

                    error?.let { message ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }

                    Button(
                        enabled = !saving,
                        onClick = {
                            val keywords = parsedKeywords
                            val intervalHours = parsedInterval
                            when {
                                name.isBlank() -> error = nameRequiredMessage
                                keywords.isEmpty() -> error = valuesRequiredMessage
                                searchType == YamiboThreadSearchType.USER_ID &&
                                    keywords.any { it.toIntOrNull()?.takeIf { id -> id > 0 } == null } -> {
                                    error = invalidUserIdMessage
                                }
                                intervalHours == null || intervalHours <= 0 -> {
                                    error = invalidAutoRefreshIntervalMessage
                                }
                                else -> scope.launch {
                                    saving = true
                                    error = null
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            if (listId == null) {
                                                database.createList(
                                                    name,
                                                    keywords,
                                                    searchType,
                                                    autoRefreshIntervalHours = intervalHours,
                                                    autoDownloadNewThreads = autoDownloadNewThreads,
                                                    autoDeleteAfterImageReading = autoDeleteAfterImageReading,
                                                )
                                            } else {
                                                database.updateList(
                                                    listId,
                                                    name,
                                                    keywords,
                                                    searchType,
                                                    autoRefreshIntervalHours = intervalHours,
                                                    autoDownloadNewThreads = autoDownloadNewThreads,
                                                    autoDeleteAfterImageReading = autoDeleteAfterImageReading,
                                                )
                                                listId
                                            }
                                        }
                                    }.onSuccess(onSaved).onFailure { failure ->
                                        if (failure is CancellationException) throw failure
                                        AppLogger.error(TAG, failure) {
                                            if (listId == null) {
                                                "Could not create custom list"
                                            } else {
                                                "Could not update custom list $listId"
                                            }
                                        }
                                        error = failure.message ?: saveFailedMessage
                                    }
                                    saving = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(if (saving) R.string.custom_list_saving else R.string.custom_list_save))
                    }

                    if (listId != null) {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = pocketCardColors(),
                            border = pocketCardBorder(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    stringResource(R.string.custom_list_manage_section),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    stringResource(R.string.custom_list_excluded_summary, excludedCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    stringResource(R.string.custom_list_clear_exclusions_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedButton(
                                        enabled = excludedCount > 0,
                                        onClick = { confirmClearExclusions = true },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.custom_list_clear_exclusions))
                                    }
                                    OutlinedButton(
                                        onClick = { confirmDelete = true },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.custom_list_delete))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClearExclusions && listId != null) {
        AlertDialog(
            onDismissRequest = { confirmClearExclusions = false },
            title = { Text(stringResource(R.string.custom_list_clear_exclusions_title)) },
            text = { Text(stringResource(R.string.custom_list_clear_exclusions_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearExclusions = false
                    scope.launch {
                        withContext(Dispatchers.IO) { database.clearExclusions(listId) }
                        excludedCount = 0
                    }
                }) {
                    Text(stringResource(R.string.custom_list_clear_exclusions))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearExclusions = false }) {
                    Text(stringResource(R.string.custom_list_cancel))
                }
            },
        )
    }

    if (confirmDelete && listId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.custom_list_delete_title)) },
            text = { Text(stringResource(R.string.custom_list_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { database.deleteList(listId) }
                        onDeleted()
                    }
                }) {
                    Text(
                        stringResource(R.string.custom_list_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.custom_list_cancel))
                }
            },
        )
    }
}

@Composable
private fun EditorSwitchTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

private const val TAG = "CustomListEditor"

private fun YamiboThreadSearchType.labelResource() = when (this) {
    YamiboThreadSearchType.KEYWORD -> R.string.custom_list_search_keyword
    YamiboThreadSearchType.TITLE -> R.string.custom_list_search_title
    YamiboThreadSearchType.USER_ID -> R.string.custom_list_search_user_id
}

private fun YamiboThreadSearchType.hintResource() = when (this) {
    YamiboThreadSearchType.KEYWORD -> R.string.custom_list_keywords_hint
    YamiboThreadSearchType.TITLE -> R.string.custom_list_titles_hint
    YamiboThreadSearchType.USER_ID -> R.string.custom_list_user_ids_hint
}
