package com.localai.chat.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localai.chat.R
import com.localai.chat.data.preferences.ThinkingDisplayMode
import com.localai.chat.data.preferences.ThemeMode

@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onApiBaseUrlChanged = viewModel::onApiBaseUrlChanged,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onDefaultModelChanged = viewModel::onDefaultModelChanged,
        onAssistantDisplayNameChanged = viewModel::onAssistantDisplayNameChanged,
        onDefaultSystemPromptChanged = viewModel::onDefaultSystemPromptChanged,
        onStreamingEnabledChanged = viewModel::onStreamingEnabledChanged,
        onThinkingDisplayModeChanged = viewModel::onThinkingDisplayModeChanged,
        onThemeModeChanged = viewModel::onThemeModeChanged,
        onChatFontSizePxChanged = viewModel::onChatFontSizePxChanged,
        onSave = viewModel::saveSettings,
        onTestConnection = viewModel::testConnection,
        onModelSelected = viewModel::onDefaultModelChanged,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onApiBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onDefaultModelChanged: (String) -> Unit,
    onAssistantDisplayNameChanged: (String) -> Unit,
    onDefaultSystemPromptChanged: (String) -> Unit,
    onStreamingEnabledChanged: (Boolean) -> Unit,
    onThinkingDisplayModeChanged: (ThinkingDisplayMode) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onChatFontSizePxChanged: (String) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onModelSelected: (String) -> Unit,
) {
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "OpenAI-compatible API",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.apiBaseUrl,
                onValueChange = onApiBaseUrlChanged,
                label = { Text(text = "API base URL") },
                supportingText = {
                    Text(text = "Example: https://api.example.com/v1 or http://localhost:8080/v1")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.apiKey,
                onValueChange = onApiKeyChanged,
                label = { Text(text = "API key (optional)") },
                supportingText = { Text(text = "Stored locally using encrypted Android preferences.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.defaultModel,
                onValueChange = onDefaultModelChanged,
                label = { Text(text = "Default model") },
                supportingText = { Text(text = "Manual entry is supported if /v1/models is unavailable.") },
                singleLine = true,
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.assistantDisplayName,
                onValueChange = onAssistantDisplayNameChanged,
                label = { Text(text = "Assistant display name") },
                supportingText = { Text(text = "Shown above assistant messages.") },
                singleLine = true,
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.defaultSystemPrompt,
                onValueChange = onDefaultSystemPromptChanged,
                label = { Text(text = "Default system prompt") },
                supportingText = { Text(text = "Optional. Sent first on every chat request when set.") },
                minLines = 2,
                maxLines = 6,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Streaming responses",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Disable this to use non-streaming chat completions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.streamingEnabled,
                    onCheckedChange = onStreamingEnabledChanged,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        TextButton(onClick = { onThemeModeChanged(mode) }) {
                            Text(
                                text = if (uiState.themeMode == mode) {
                                    "${mode.name} ✓"
                                } else {
                                    mode.name
                                },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Thinking blocks",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Controls display of model output inside <think>...</think> tags.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThinkingDisplayMode.entries.forEach { mode ->
                        TextButton(onClick = { onThinkingDisplayModeChanged(mode) }) {
                            Text(
                                text = if (uiState.thinkingDisplayMode == mode) {
                                    "${mode.name} ✓"
                                } else {
                                    mode.name
                                },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uiState.chatFontSizePx,
                onValueChange = onChatFontSizePxChanged,
                label = { Text(text = "Chat/message font size (px)") },
                supportingText = { Text(text = "Applies only to chat message text. Suggested range: 12-32px.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Text(
                text = "Preview: The quick brown fox jumps over the local model.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = (uiState.chatFontSizePx.toIntOrNull()?.coerceIn(12, 32) ?: 18).sp,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onTestConnection,
                    enabled = !uiState.isTestingConnection,
                ) {
                    if (uiState.isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(text = "Test connection")
                    }
                }

                TextButton(
                    onClick = onSave,
                    enabled = !uiState.isSaving,
                ) {
                    Text(text = if (uiState.isSaving) "Saving..." else "Save")
                }
            }

            uiState.status?.let { status ->
                SettingsStatusCard(status = status)
            }

            if (uiState.availableModels.isNotEmpty()) {
                Text(
                    text = "Available models",
                    style = MaterialTheme.typography.titleMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.availableModels.take(12).forEach { model ->
                        AssistChip(
                            onClick = { onModelSelected(model) },
                            label = { Text(text = model) },
                        )
                    }
                    if (uiState.availableModels.size > 12) {
                        Text(
                            text = "${uiState.availableModels.size - 12} more models found. Enter the desired name manually above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = "Cleartext HTTP is allowed for local/self-hosted endpoints. HTTPS is still preferred outside trusted local or VPN networks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(onClick = { showAboutDialog = true }) {
                Text(text = "About")
            }
        }

        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "About Local AI Chat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "I wanted a simple app for talking to my local model remotely, but every option I found was either a buggy PWA or more complicated than I needed. This app is intentionally bare bones: no tools, no MCP, no sampling controls, just direct conversation with an OpenAI-compatible API and the model weights behind it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "This app may have bugs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Version: v1.0\nLicense: MIT",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { uriHandler.openUri(GithubUrl) },
                ) {
                    Text(text = "GitHub: $GithubUrl")
                }
                Image(
                    modifier = Modifier
                        .width(140.dp)
                        .height(92.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    painter = painterResource(id = R.drawable.aboutsection),
                    contentDescription = "About easter egg",
                    contentScale = ContentScale.Crop,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close")
            }
        },
    )
}

private const val GithubUrl = "https://github.com/kerniqqi-cloud/Local-AI-chat"

@Composable
private fun SettingsStatusCard(status: SettingsStatus) {
    val colors = when (status) {
        is SettingsStatus.Success -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        is SettingsStatus.Warning -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        is SettingsStatus.Error -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    val message = when (status) {
        is SettingsStatus.Success -> status.message
        is SettingsStatus.Warning -> status.message
        is SettingsStatus.Error -> status.message
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = colors,
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = message,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
