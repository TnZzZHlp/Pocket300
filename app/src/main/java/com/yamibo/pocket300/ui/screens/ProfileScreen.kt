package com.yamibo.pocket300.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yamibo.pocket300.api.resolveYamiboAvatarUrl
import com.yamibo.pocket300.ui.rememberPostImageRequest
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.DEFAULT_SECURITY_QUESTIONS
import com.yamibo.pocket300.api.LoginInput
import com.yamibo.pocket300.api.SecurityQuestionOption
import com.yamibo.pocket300.api.YamiboDailyCheckInState
import com.yamibo.pocket300.api.YamiboDailyCheckInStatus
import com.yamibo.pocket300.api.YamiboSession
import com.yamibo.pocket300.logging.AppLogger
import com.yamibo.pocket300.api.YamiboUserProfile
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.Loading
import com.yamibo.pocket300.ui.LocalDownloadedThreadIds
import com.yamibo.pocket300.ui.LocalReadingHistory
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.components.SectionLabel
import com.yamibo.pocket300.ui.load
import com.yamibo.pocket300.ui.theme.pocketCardBorder
import com.yamibo.pocket300.ui.theme.pocketCardColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    scrollToTopTrigger: Int = 0,
    onAuthStateChanged: () -> Unit,
    onDownloads: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    var sessionState: LoadState<YamiboSession?> by remember { mutableStateOf(LoadState.Loading) }
    var sessionReload by remember { mutableIntStateOf(0) }
    var summaryRefreshToken by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val downloadCount = LocalDownloadedThreadIds.current.size
    val historyCount = LocalReadingHistory.current.size
    val isLoggedIn = (sessionState as? LoadState.Ready)?.value != null

    LaunchedEffect(sessionReload) {
        sessionState = load { api.auth.getCurrentSession() }
    }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
    }

    ScreenScaffold(
        stringResource(R.string.profile_title),
        onRefresh = if (isLoggedIn) {
            {
                isRefreshing = true
                summaryRefreshToken++
            }
        } else {
            null
        },
        isRefreshing = isRefreshing,
        onSettings = onSettings,
        onTopBarDoubleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
    ) { padding ->
        when (val current = sessionState) {
            LoadState.Loading -> ProfileLocalActionsPanel(
                modifier = Modifier.padding(padding),
                downloadCount = downloadCount,
                historyCount = historyCount,
                onDownloads = onDownloads,
                onHistory = onHistory,
            ) {
                Loading()
            }

            is LoadState.Failed -> ProfileLocalActionsPanel(
                modifier = Modifier.padding(padding),
                downloadCount = downloadCount,
                historyCount = historyCount,
                onDownloads = onDownloads,
                onHistory = onHistory,
            ) {
                EmptyState(
                    stringResource(R.string.profile_auth_status_unavailable),
                    current.message,
                    onRetry = {
                        sessionState = LoadState.Loading
                        sessionReload++
                    },
                )
            }

            is LoadState.Ready -> if (current.value == null) {
                LoginPanel(
                    modifier = Modifier.padding(padding),
                    downloadCount = downloadCount,
                    historyCount = historyCount,
                    onDownloads = onDownloads,
                    onHistory = onHistory,
                    listState = listState,
                ) {
                    sessionState = LoadState.Ready(it)
                    onAuthStateChanged()
                }
            } else {
                ProfileSummary(
                    session = current.value,
                    modifier = Modifier.padding(padding),
                    downloadCount = downloadCount,
                    historyCount = historyCount,
                    refreshToken = summaryRefreshToken,
                    onRefreshFinished = { isRefreshing = false },
                    onDownloads = onDownloads,
                    onHistory = onHistory,
                    onLoggedOut = {
                        isRefreshing = false
                        sessionState = LoadState.Ready(null)
                        onAuthStateChanged()
                    },
                    listState = listState,
                )
            }
        }
    }
}

@Composable
private fun ProfileLocalActionsPanel(
    modifier: Modifier,
    downloadCount: Int,
    historyCount: Int,
    onDownloads: () -> Unit,
    onHistory: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileLocalLibraryCard(
            downloadCount = downloadCount,
            historyCount = historyCount,
            onDownloads = onDownloads,
            onHistory = onHistory,
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginPanel(
    modifier: Modifier,
    downloadCount: Int,
    historyCount: Int,
    onDownloads: () -> Unit,
    onHistory: () -> Unit,
    listState: LazyListState,
    onLoggedIn: (YamiboSession) -> Unit,
) {
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var selectedQuestion by rememberSaveable { mutableStateOf(0) }
    var questions by remember {
        mutableStateOf<List<SecurityQuestionOption>>(
            DEFAULT_SECURITY_QUESTIONS
        )
    }
    var expanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        questions = runCatching { api.auth.getLoginSecurityQuestions() }.getOrElse { failure ->
            if (failure is CancellationException) throw failure
            AppLogger.warn(TAG, failure) {
                "Could not load login security questions; using built-in defaults"
            }
            DEFAULT_SECURITY_QUESTIONS
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
            ) {
                Surface(
                    Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "百",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.profile_login_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.profile_login_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = pocketCardColors(),
                border = pocketCardBorder(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.profile_account_login),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedTextField(
                        account,
                        { account = it },
                        label = { Text(stringResource(R.string.profile_account_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        password,
                        { password = it },
                        label = { Text(stringResource(R.string.profile_password_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = questions.firstOrNull { it.id == selectedQuestion }?.label.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.profile_security_question_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }) {
                            questions.forEach { question ->
                                DropdownMenuItem(
                                    text = { Text(question.label) },
                                    onClick = {
                                        selectedQuestion = question.id
                                        if (question.id == 0) answer = ""
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                    if (selectedQuestion != 0) {
                        OutlinedTextField(
                            answer,
                            { answer = it },
                            label = { Text(stringResource(R.string.profile_security_answer_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = { submitting = true; error = null },
                        enabled = account.isNotBlank() && password.isNotBlank() && !submitting,
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(10.dp))
                        }
                        Text(
                            stringResource(
                                if (submitting) R.string.profile_logging_in
                                else R.string.profile_login_action,
                            ),
                        )
                    }
                }
            }
        }
        item {
            ProfileLocalLibraryCard(
                downloadCount = downloadCount,
                historyCount = historyCount,
                onDownloads = onDownloads,
                onHistory = onHistory,
            )
        }
    }
    if (submitting) LaunchedEffect(account, password, selectedQuestion, answer) {
        when (val result =
            load { api.auth.login(LoginInput(account, password, answer, selectedQuestion)) }) {
            is LoadState.Ready -> onLoggedIn(result.value)
            is LoadState.Failed -> error = result.message
            LoadState.Loading -> Unit
        }
        submitting = false
    }
}

@Composable
private fun ProfileSummary(
    session: YamiboSession,
    modifier: Modifier,
    downloadCount: Int,
    historyCount: Int,
    refreshToken: Int,
    onRefreshFinished: () -> Unit,
    onDownloads: () -> Unit,
    onHistory: () -> Unit,
    onLoggedOut: () -> Unit,
    listState: LazyListState,
) {
    var profile: LoadState<YamiboUserProfile> by remember { mutableStateOf(LoadState.Loading) }
    var checkInReload by remember { mutableIntStateOf(0) }
    var checkIn: LoadState<YamiboDailyCheckInStatus> by remember {
        mutableStateOf(LoadState.Loading)
    }
    var checkingIn by remember { mutableStateOf(false) }
    var logoutConfirmVisible by rememberSaveable { mutableStateOf(false) }
    var loggingOut by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val logoutFailedMessage = stringResource(R.string.profile_logout_failed)

    LaunchedEffect(session.uid, refreshToken) {
        try {
            coroutineScope {
                val profileDeferred = async { load { api.auth.getUserProfile(session.uid) } }
                val checkInDeferred = async { load { api.dailyCheckIn.getStatus() } }
                profile = profileDeferred.await()
                checkIn = checkInDeferred.await()
            }
        } finally {
            onRefreshFinished()
        }
    }
    LaunchedEffect(session.uid, checkInReload) {
        if (checkInReload > 0) {
            checkIn = LoadState.Loading
            checkIn = load { api.dailyCheckIn.getStatus() }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            val loadedProfile = (profile as? LoadState.Ready)?.value
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = pocketCardBorder(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            Modifier.size(72.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    session.username.take(1),
                                    style = MaterialTheme.typography.headlineLarge
                                )
                                loadedProfile?.avatarUrl?.let { avatarUrl ->
                                    AsyncImage(
                                        model = rememberPostImageRequest(resolveYamiboAvatarUrl(avatarUrl)),
                                        contentDescription = stringResource(R.string.post_author_avatar, session.username),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                    )
                                }
                            }
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                loadedProfile?.displayName?.takeIf { it.isNotBlank() }
                                    ?: session.username,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (loadedProfile?.displayName != null && loadedProfile.displayName != session.username) {
                                Text(
                                    session.username,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)) {
                            ProfileMetric(
                                stringResource(R.string.profile_uid_label),
                                session.uid.toString(),
                                Modifier.weight(1f),
                            )
                            ProfileMetric(
                                stringResource(R.string.profile_read_access_label),
                                session.readAccess.toString(),
                                Modifier.weight(1f),
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { logoutConfirmVisible = true },
                        enabled = !loggingOut,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(
                                if (loggingOut) R.string.profile_logging_out
                                else R.string.profile_logout_action,
                            ),
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item {
            DailyCheckInCard(
                state = checkIn,
                submitting = checkingIn,
                onCheckIn = { checkingIn = true },
                onRetry = { checkInReload++ },
            )
        }
        item {
            ProfileLocalLibraryCard(
                downloadCount = downloadCount,
                historyCount = historyCount,
                onDownloads = onDownloads,
                onHistory = onHistory,
            )
        }
        item {
            Box(Modifier.fillMaxWidth().widthIn(max = 720.dp)) {
                SectionLabel(stringResource(R.string.profile_details_section))
            }
        }
        when (val current = profile) {
            LoadState.Loading -> item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            is LoadState.Failed -> item {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = pocketCardColors(),
                    border = pocketCardBorder(),
                ) {
                    Text(
                        current.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            is LoadState.Ready -> if (current.value.fields.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.profile_details_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = pocketCardColors(),
                        border = pocketCardBorder(),
                    ) {
                        Column {
                            current.value.fields.forEachIndexed { index, field ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        field.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(field.value, style = MaterialTheme.typography.bodyLarge)
                                }
                                if (index < current.value.fields.lastIndex) {
                                    HorizontalDivider(
                                        Modifier.padding(horizontal = 18.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (logoutConfirmVisible) {
        AlertDialog(
            onDismissRequest = { logoutConfirmVisible = false },
            title = { Text(stringResource(R.string.profile_logout_confirm_title)) },
            text = { Text(stringResource(R.string.profile_logout_confirm_message)) },
            dismissButton = {
                TextButton(onClick = { logoutConfirmVisible = false }) {
                    Text(stringResource(R.string.logs_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        logoutConfirmVisible = false
                        loggingOut = true
                    },
                ) {
                    Text(stringResource(R.string.profile_logout_action))
                }
            },
        )
    }
    if (loggingOut) LaunchedEffect(Unit) {
        runCatching { api.auth.logout() }
            .onSuccess { onLoggedOut() }
            .onFailure { failure ->
                if (failure is CancellationException) throw failure
                AppLogger.warn(TAG, failure) { "Logout failed" }
                error = failure.message ?: logoutFailedMessage
            }
        loggingOut = false
    }
    if (checkingIn) LaunchedEffect(Unit) {
        checkIn = load { api.dailyCheckIn.checkIn() }
        checkingIn = false
    }
}

private const val TAG = "ProfileScreen"

@Composable
private fun DailyCheckInCard(
    state: LoadState<YamiboDailyCheckInStatus>,
    submitting: Boolean,
    onCheckIn: () -> Unit,
    onRetry: () -> Unit,
) {
    val checkedIn = (state as? LoadState.Ready)?.value?.state ==
        YamiboDailyCheckInState.CHECKED_IN
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        shape = MaterialTheme.shapes.large,
        colors = pocketCardColors(),
        border = pocketCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                if (checkedIn) Icons.Rounded.CheckCircle else Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.profile_daily_check_in),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    when (state) {
                        LoadState.Loading -> stringResource(R.string.profile_daily_check_in_loading)
                        is LoadState.Failed -> state.message
                        is LoadState.Ready -> stringResource(
                            if (checkedIn) R.string.profile_daily_check_in_done
                            else R.string.profile_daily_check_in_description,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state is LoadState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            when (state) {
                LoadState.Loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                is LoadState.Failed -> OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.profile_daily_check_in_retry))
                }
                is LoadState.Ready -> Button(
                    onClick = onCheckIn,
                    enabled = !checkedIn && !submitting,
                ) {
                    if (submitting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        stringResource(
                            when {
                                checkedIn -> R.string.profile_daily_check_in_done
                                submitting -> R.string.profile_daily_check_in_submitting
                                else -> R.string.profile_daily_check_in_action
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileLocalLibraryCard(
    downloadCount: Int,
    historyCount: Int,
    onDownloads: () -> Unit,
    onHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel(stringResource(R.string.profile_local_section))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = pocketCardColors(),
            border = pocketCardBorder(),
        ) {
            Column {
                ProfileLocalLibraryRow(
                    icon = Icons.Rounded.DownloadForOffline,
                    title = R.string.profile_downloads,
                    description = R.string.profile_downloads_description,
                    count = downloadCount,
                    onClick = onDownloads,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                ProfileLocalLibraryRow(
                    icon = Icons.Rounded.History,
                    title = R.string.profile_reading_history,
                    description = R.string.profile_reading_history_description,
                    count = historyCount,
                    onClick = onHistory,
                )
            }
        }
    }
}

@Composable
private fun ProfileLocalLibraryRow(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes description: Int,
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (count > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = stringResource(R.string.profile_count_badge, count),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
