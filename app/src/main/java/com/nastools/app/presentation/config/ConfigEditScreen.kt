package com.nastools.app.presentation.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nastools.app.presentation.components.NasConfirmDialog
import com.nastools.app.presentation.components.NasErrorState
import com.nastools.app.presentation.components.NasScaffold
import com.nastools.app.presentation.components.NasTopAppBar
import com.nastools.app.presentation.components.nasAnimateContentSize
import com.nastools.app.presentation.components.rememberNasMotionEnabled
import com.nastools.app.presentation.theme.NasSpacing
import com.nastools.app.presentation.theme.NasToolsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditScreen(
    configId: String?,
    viewModel: ConfigEditViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordVisible by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val motionEnabled = rememberNasMotionEnabled()

    LaunchedEffect(configId) {
        viewModel.load(configId)
    }

    val transientMessage = uiState.errorMessage ?: uiState.testMessage
    LaunchedEffect(transientMessage) {
        if (!transientMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(transientMessage)
            viewModel.clearTransientMessage()
        }
    }

    if (showDeleteDialog) {
        NasConfirmDialog(
            title = "删除连接",
            message = "删除后相关上传预设也会被移除，且此操作无法撤销。",
            confirmText = "删除",
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete(onBack)
            },
            onDismiss = { showDeleteDialog = false },
            icon = Icons.Default.Delete
        )
    }

    NasScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NasTopAppBar(
                title = if (configId == null) "新建连接" else "编辑连接",
                subtitle = "WebDAV 地址和认证信息",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (uiState.loadErrorMessage == null) {
                        if (configId != null) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, "删除")
                            }
                        }
                        IconButton(onClick = { viewModel.save(onBack) }) {
                            Icon(Icons.Default.Check, "保存")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.loadErrorMessage != null && !uiState.isLoading) {
            NasErrorState(
                title = "加载连接失败",
                message = uiState.loadErrorMessage ?: "无法加载连接配置",
                onRetry = viewModel::retryLoad,
                modifier = Modifier.padding(padding)
            )
        } else {
            Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(NasSpacing.lg)
                .nasAnimateContentSize(motionEnabled),
            verticalArrangement = Arrangement.spacedBy(NasSpacing.md)
        ) {
            if (uiState.isLoading || uiState.isSaving || uiState.isTesting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("连接名称") },
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("WebDAV 地址") },
                placeholder = { Text("https://example.com/dav") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true
            )
            if (uiState.baseUrl.trim().startsWith("http://", ignoreCase = true)) {
                Text(
                    "当前使用 HTTP 明文连接，密码和文件名可能被局域网内其他设备截获；建议优先使用 HTTPS。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::updateUsername,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("用户名") },
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::updatePassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            if (passwordVisible) "隐藏密码" else "显示密码"
                        )
                    }
                },
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.defaultRemotePath,
                onValueChange = viewModel::updateDefaultRemotePath,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("默认远端目录") },
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("信任自签名证书", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "仅对当前主机放宽证书校验",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.trustSelfSigned,
                    onCheckedChange = viewModel::updateTrustSelfSigned
                )
            }

            Spacer(Modifier.height(NasSpacing.xs))

            Button(
                onClick = viewModel::testConnection,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isTesting && !uiState.isSaving
            ) {
                Text(if (uiState.isTesting) "测试中..." else "测试连接")
            }

            Button(
                onClick = { viewModel.save(onBack) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving && !uiState.isTesting
            ) {
                Text(if (uiState.isSaving) "保存中..." else "保存")
            }
        }
        }
    }
}

@Preview(name = "连接编辑 · 浅色", showBackground = true)
@Composable
private fun ConfigEditLightPreview() {
    NasToolsTheme(darkTheme = false) { ConfigEditPreviewContent() }
}

@Preview(name = "连接编辑 · 深色", showBackground = true, backgroundColor = 0xFF101413)
@Composable
private fun ConfigEditDarkPreview() {
    NasToolsTheme(darkTheme = true) { ConfigEditPreviewContent() }
}

@Composable
private fun ConfigEditPreviewContent() {
    NasScaffold(
        topBar = {
            NasTopAppBar(title = "编辑连接", subtitle = "WebDAV 地址和认证信息")
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(NasSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NasSpacing.md)
        ) {
            OutlinedTextField(value = "家庭 NAS", onValueChange = {}, label = { Text("连接名称") })
            OutlinedTextField(value = "https://example.com/dav", onValueChange = {}, label = { Text("WebDAV 地址") })
            OutlinedTextField(value = "han", onValueChange = {}, label = { Text("用户名") })
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("测试连接") }
        }
    }
}
