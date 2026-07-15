package com.yamibo.pocket300.ui.screens

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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yamibo.pocket300.R
import com.yamibo.pocket300.api.DEFAULT_SECURITY_QUESTIONS
import com.yamibo.pocket300.api.LoginInput
import com.yamibo.pocket300.api.SecurityQuestionOption
import com.yamibo.pocket300.api.YamiboSession
import com.yamibo.pocket300.api.YamiboUserProfile
import com.yamibo.pocket300.ui.EmptyState
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.Loading
import com.yamibo.pocket300.ui.ScreenScaffold
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.components.SectionLabel
import com.yamibo.pocket300.ui.load
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    onAuthStateChanged: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    var sessionState: LoadState<YamiboSession?> by remember { mutableStateOf(LoadState.Loading) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { sessionState = load { api.auth.getCurrentSession() } }

    ScreenScaffold(
        "我的",
        onSettings = onSettings,
        onTopBarDoubleClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
    ) { padding ->
        when (val current = sessionState) {
            LoadState.Loading -> Loading(Modifier.padding(padding))
            is LoadState.Failed -> EmptyState(
                "无法读取登录状态",
                current.message,
                Modifier.padding(padding)
            )

            is LoadState.Ready -> if (current.value == null) {
                LoginPanel(Modifier.padding(padding), onHistory, listState) {
                    sessionState = LoadState.Ready(it)
                    onAuthStateChanged()
                }
            } else {
                ProfileSummary(
                    session = current.value,
                    modifier = Modifier.padding(padding),
                    onHistory = onHistory,
                    onLoggedOut = {
                        sessionState = LoadState.Ready(null)
                        onAuthStateChanged()
                    },
                    listState = listState,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginPanel(
    modifier: Modifier,
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
        questions = runCatching { api.auth.getLoginSecurityQuestions() }.getOrDefault(
            DEFAULT_SECURITY_QUESTIONS
        )
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
                    "登录百合会",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "连接账号后查看个人信息、私信和收藏内容。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            ElevatedCard(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "账号登录",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedTextField(
                        account,
                        { account = it },
                        label = { Text("账号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        password,
                        { password = it },
                        label = { Text("密码") },
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
                            label = { Text("安全提问") },
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
                            label = { Text("安全提问答案") },
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
                        Text(if (submitting) "正在登录…" else "登录")
                    }
                }
            }
        }
        item { ProfileHistoryItem(onHistory) }
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
    onHistory: () -> Unit,
    onLoggedOut: () -> Unit,
    listState: LazyListState,
) {
    var reload by remember { mutableStateOf(0) }
    var profile: LoadState<YamiboUserProfile> by remember { mutableStateOf(LoadState.Loading) }
    var loggingOut by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.uid, reload) { profile = load { api.auth.getUserProfile(session.uid) } }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            val loadedProfile = (profile as? LoadState.Ready)?.value
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                                        model = avatarUrl,
                                        contentDescription = "头像",
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
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)) {
                            ProfileMetric("UID", session.uid.toString(), Modifier.weight(1f))
                            ProfileMetric(
                                "阅读权限",
                                session.readAccess.toString(),
                                Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(onClick = { reload++ }, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("刷新资料")
                        }
                        OutlinedButton(
                            onClick = { loggingOut = true },
                            enabled = !loggingOut,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null,
                                Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(if (loggingOut) "正在退出…" else "退出登录")
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item { ProfileHistoryItem(onHistory) }
        item { Box(Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)) { SectionLabel("个人资料") } }
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
                Card(Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)) {
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
                        "资料页没有解析到可展示字段",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                        shape = RoundedCornerShape(20.dp)
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
    if (loggingOut) LaunchedEffect(Unit) {
        runCatching { api.auth.logout() }
            .onSuccess { onLoggedOut() }
            .onFailure { error = it.message ?: "退出失败" }
        loggingOut = false
    }
}

@Composable
private fun ProfileHistoryItem(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.profile_reading_history),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.profile_reading_history_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
