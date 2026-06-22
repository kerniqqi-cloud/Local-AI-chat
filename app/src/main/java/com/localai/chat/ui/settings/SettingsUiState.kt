package com.localai.chat.ui.settings

import com.localai.chat.data.preferences.ThinkingDisplayMode
import com.localai.chat.data.preferences.ThemeMode

data class SettingsUiState(
    val apiBaseUrl: String = "",
    val apiKey: String = "",
    val defaultModel: String = "",
    val assistantDisplayName: String = "Assistant",
    val defaultSystemPrompt: String = "",
    val streamingEnabled: Boolean = true,
    val thinkingDisplayMode: ThinkingDisplayMode = ThinkingDisplayMode.Collapsed,
    val themeMode: ThemeMode = ThemeMode.System,
    val chatFontSizePx: String = "18",
    val availableModels: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val isTestingConnection: Boolean = false,
    val status: SettingsStatus? = null,
)

sealed interface SettingsStatus {
    data class Success(val message: String) : SettingsStatus
    data class Warning(val message: String) : SettingsStatus
    data class Error(val message: String) : SettingsStatus
}
