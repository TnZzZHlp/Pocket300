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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen() {
    var sessionState: LoadState<YamiboSession?> by remember { mutableStateOf(LoadState.Loading) }
    LaunchedEffect(Unit) { sessionState = load { api.auth.getCurrentSession() } }

    ScreenScaffold("我的") { padding ->
        when (val current = sessionState) {
            LoadState.Loading -> Loading(Modifier.padding(padding))
            is LoadState.Failed -> EmptyState("无法读取登录状态", current.message, Modifier.padding(padding))
            is LoadState.Ready -> if (current.value == null) {
                LoginPanel(Modifier.padding(padding)) { sessionState = LoadState.Ready(it) }
            } else {
                ProfileSummary(
                    session = current.value,
                    modifier = Modifier.padding(padding),
                    onLoggedOut = { sessionState = LoadState.Ready(null) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginPanel(modifier: Modifier, onLoggedIn: (YamiboSession) -> Unit) {
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var selectedQuestion by rememberSaveable { mutableStateOf(0) }
    var questions by remember { mutableStateOf<List<SecurityQuestionOption>>(DEFAULT_SECURITY_QUESTIONS) }
    var expanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        questions = runCatching { api.auth.getLoginSecurityQuestions() }.getOrDefault(DEFAULT_SECURITY_QUESTIONS)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(Modifier.size(72.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text("百", style = MaterialTheme.typography.headlineLarge) }
                }
                Spacer(Modifier.height(12.dp))
                Text("登录百合会", style = MaterialTheme.typography.headlineMedium)
                Text("连接账号后查看个人信息、私信和收藏内容。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            ElevatedCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("账号登录", style = MaterialTheme.typography.titleLarge)
                    Text("登录状态由原生网络层的 HttpOnly Cookie 保存。", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(account, { account = it }, label = { Text("账号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        password,
                        { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = questions.firstOrNull { it.id == selectedQuestion }?.label.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("安全提问") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (submitting) "正在登录…" else "登录") }
                }
            }
        }
    }
    if (submitting) LaunchedEffect(account, password, selectedQuestion, answer) {
        when (val result = load { api.auth.login(LoginInput(account, password, answer, selectedQuestion)) }) {
            is LoadState.Ready -> onLoggedIn(result.value)
            is LoadState.Failed -> error = result.message
            LoadState.Loading -> Unit
        }
        submitting = false
    }
}

@Composable
private fun ProfileSummary(session: YamiboSession, modifier: Modifier, onLoggedOut: () -> Unit) {
    var reload by remember { mutableStateOf(0) }
    var profile: LoadState<YamiboUserProfile> by remember { mutableStateOf(LoadState.Loading) }
    var loggingOut by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.uid, reload) { profile = load { api.auth.getUserProfile(session.uid) } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(Modifier.size(72.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                        Box(contentAlignment = Alignment.Center) { Text(session.username.take(1), style = MaterialTheme.typography.headlineLarge) }
                    }
                    Text(session.username, style = MaterialTheme.typography.headlineMedium)
                    Text("UID ${session.uid} · 阅读权限 ${session.readAccess}")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { reload++ }) { Text("刷新资料") }
                        OutlinedButton(onClick = { loggingOut = true }, enabled = !loggingOut) {
                            Text(if (loggingOut) "正在退出…" else "退出登录")
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item { SectionLabel("个人资料") }
        when (val current = profile) {
            LoadState.Loading -> item { Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            is LoadState.Failed -> item { Text(current.message, color = MaterialTheme.colorScheme.error) }
            is LoadState.Ready -> if (current.value.fields.isEmpty()) {
                item { Text("资料页没有解析到可展示字段", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(current.value.fields) { field ->
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(field.value, style = MaterialTheme.typography.bodyLarge)
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
