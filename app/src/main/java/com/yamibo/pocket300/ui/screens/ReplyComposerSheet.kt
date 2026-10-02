package com.yamibo.pocket300.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReplyComposerSheet(
    title: String,
    target: String,
    hint: String,
    submitLabel: String,
    submittingLabel: String,
    draft: String,
    submitting: Boolean,
    threadClosed: Boolean,
    onDraftChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    maxLength: Int? = null,
) {
    val currentSubmitting = rememberUpdatedState(submitting)
    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = { it != SheetValue.Hidden || !currentSubmitting.value },
    )
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val editorEnabled = !submitting && !threadClosed
    val dismiss: () -> Unit = {
        if (!currentSubmitting.value) {
            scope.launch {
                sheetState.hide()
                if (!sheetState.isVisible) onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!currentSubmitting.value) onDismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = !submitting,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
            shouldDismissOnClickOutside = !submitting,
        ),
    ) {
        // The sheet already applies IME insets; adding imePadding here would double them.
        val imeVisible = WindowInsets.isImeVisible
        BackHandler {
            if (imeVisible) keyboard?.hide() else dismiss()
        }
        LaunchedEffect(sheetState, threadClosed) {
            snapshotFlow { sheetState.isVisible }.first { it }
            if (editorEnabled) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
        }
        LaunchedEffect(imeVisible) {
            if (imeVisible) sheetState.expand()
        }
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = dismiss, enabled = !submitting) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.thread_composer_close),
                    )
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = onSubmit,
                    enabled = canSubmitThreadReply(draft, submitting, threadClosed) &&
                        (maxLength == null || draft.length <= maxLength),
                ) {
                    if (submitting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (submitting) submittingLabel else submitLabel)
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    target,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (threadClosed) {
                    Text(
                        stringResource(R.string.thread_reply_closed),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { proposed ->
                            if (maxLength == null || proposed.length <= maxLength) {
                                onDraftChange(proposed)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        enabled = editorEnabled,
                        minLines = 3,
                        maxLines = 12,
                        placeholder = { Text(hint) },
                        supportingText = maxLength?.let { limit ->
                            {
                                Text(
                                    stringResource(
                                        R.string.thread_comment_character_count,
                                        draft.length,
                                        limit,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
