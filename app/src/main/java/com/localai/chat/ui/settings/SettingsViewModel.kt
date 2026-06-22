package com.localai.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localai.chat.core.error.AppError
import com.localai.chat.data.preferences.SettingsRepository
import com.localai.chat.data.preferences.ThinkingDisplayMode
import com.localai.chat.data.preferences.ThemeMode
import com.localai.chat.data.secure.ApiKeyStore
import com.localai.chat.network.openai.ApiResult
import com.localai.chat.network.openai.OpenAiApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val openAiApiClient: OpenAiApiClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        apiBaseUrl = settings.apiBaseUrl,
                        defaultModel = settings.defaultModel,
                        assistantDisplayName = settings.assistantDisplayName,
                        defaultSystemPrompt = settings.defaultSystemPrompt,
                        streamingEnabled = settings.streamingEnabled,
                        thinkingDisplayMode = settings.thinkingDisplayMode,
                        themeMode = settings.themeMode,
                        chatFontSizePx = settings.chatFontSizePx.toString(),
                    )
                }
            }
        }

        viewModelScope.launch {
            runCatching { apiKeyStore.getApiKey().orEmpty() }
                .onSuccess { apiKey -> _uiState.update { it.copy(apiKey = apiKey) } }
                .onFailure {
                    _uiState.update {
                        it.copy(status = SettingsStatus.Error(AppError.SecureStorage().userMessage))
                    }
                }
        }
    }

    fun onApiBaseUrlChanged(value: String) {
        _uiState.update { it.copy(apiBaseUrl = value, status = null) }
    }

    fun onApiKeyChanged(value: String) {
        _uiState.update { it.copy(apiKey = value, status = null) }
    }

    fun onDefaultModelChanged(value: String) {
        _uiState.update { it.copy(defaultModel = value, status = null) }
    }

    fun onAssistantDisplayNameChanged(value: String) {
        _uiState.update { it.copy(assistantDisplayName = value, status = null) }
    }

    fun onDefaultSystemPromptChanged(value: String) {
        _uiState.update { it.copy(defaultSystemPrompt = value, status = null) }
    }

    fun onStreamingEnabledChanged(value: Boolean) {
        _uiState.update { it.copy(streamingEnabled = value, status = null) }
    }

    fun onThinkingDisplayModeChanged(value: ThinkingDisplayMode) {
        _uiState.update { it.copy(thinkingDisplayMode = value, status = null) }
    }

    fun onThemeModeChanged(value: ThemeMode) {
        _uiState.update { it.copy(themeMode = value, status = null) }
    }

    fun onChatFontSizePxChanged(value: String) {
        val sanitized = value.filter(Char::isDigit).take(2)
        _uiState.update { it.copy(chatFontSizePx = sanitized, status = null) }
    }

    fun saveSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, status = null) }
            runCatching {
                settingsRepository.saveConnectionSettings(
                    apiBaseUrl = openAiApiClient.normalizeBaseUrl(state.apiBaseUrl),
                    defaultModel = state.defaultModel,
                    assistantDisplayName = state.assistantDisplayName,
                    defaultSystemPrompt = state.defaultSystemPrompt,
                    streamingEnabled = state.streamingEnabled,
                    thinkingDisplayMode = state.thinkingDisplayMode,
                    themeMode = state.themeMode,
                    chatFontSizePx = state.chatFontSizePx.toIntOrNull()?.coerceIn(12, 32) ?: 18,
                )
                apiKeyStore.setApiKey(state.apiKey)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        status = SettingsStatus.Success("Settings saved."),
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        status = SettingsStatus.Error("Settings could not be saved."),
                    )
                }
            }
        }
    }

    fun testConnection() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, status = null) }
            when (val result = openAiApiClient.listModels(state.apiBaseUrl, state.apiKey)) {
                is ApiResult.Success -> {
                    val models = result.value
                    _uiState.update { current ->
                        current.copy(
                            availableModels = models,
                            defaultModel = current.defaultModel.ifBlank { models.firstOrNull().orEmpty() },
                            isTestingConnection = false,
                            status = SettingsStatus.Success(
                                if (models.isEmpty()) {
                                    "Connected, but no models were returned. Enter a model manually."
                                } else {
                                    "Connected. Found ${models.size} model${if (models.size == 1) "" else "s"}."
                                },
                            ),
                        )
                    }
                }
                is ApiResult.Failure -> {
                    val status = if (result.error is AppError.ModelsUnsupported) {
                        SettingsStatus.Warning(result.error.userMessage)
                    } else {
                        SettingsStatus.Error(result.error.userMessage)
                    }
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            status = status,
                        )
                    }
                }
            }
        }
    }
}
