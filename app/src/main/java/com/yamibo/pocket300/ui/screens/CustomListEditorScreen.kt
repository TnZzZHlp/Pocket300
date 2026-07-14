package com.yamibo.pocket300.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import com.yamibo.pocket300.data.CustomListDatabase
import com.yamibo.pocket300.data.normalizeCustomListKeywords
import com.yamibo.pocket300.ui.Loading
import com.yamibo.pocket300.ui.ScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val keywordsRequiredMessage = stringResource(R.string.custom_list_keywords_required)
    val saveFailedMessage = stringResource(R.string.custom_list_save_failed)
    var name by rememberSaveable(listId) { mutableStateOf("") }
    var keywordText by rememberSaveable(listId) { mutableStateOf("") }
    var excludedCount by rememberSaveable(listId) { mutableIntStateOf(0) }
    var loading by remember(listId) { mutableStateOf(listId != null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(listId) {
        if (listId != null) {
            val list = withContext(Dispatchers.IO) { database.getList(listId) }
            if (list == null) {
                error = notFoundMessage
            } else {
                name = list.name
                keywordText = list.keywords.joinToString("\n")
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
            Column(
                Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.custom_list_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = keywordText,
                    onValueChange = { keywordText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.custom_list_keywords)) },
                    supportingText = { Text(stringResource(R.string.custom_list_keywords_hint)) },
                    minLines = 4,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    enabled = !saving,
                    onClick = {
                        val keywords = normalizeCustomListKeywords(keywordText)
                        when {
                            name.isBlank() -> error = nameRequiredMessage
                            keywords.isEmpty() -> error = keywordsRequiredMessage
                            else -> scope.launch {
                                saving = true
                                error = null
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        if (listId == null) database.createList(name, keywords)
                                        else listId.also { database.updateList(it, name, keywords) }
                                    }
                                }.onSuccess(onSaved).onFailure {
                                    error = it.message ?: saveFailedMessage
                                }
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (saving) R.string.custom_list_saving else R.string.custom_list_save))
                }
                if (listId != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            enabled = excludedCount > 0,
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { database.clearExclusions(listId) }
                                    excludedCount = 0
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.custom_list_clear_exclusions)) }
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.custom_list_delete)) }
                    }
                    Text(
                        stringResource(R.string.custom_list_clear_exclusions_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
                }) { Text(stringResource(R.string.custom_list_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.custom_list_cancel))
                }
            },
        )
    }
}
