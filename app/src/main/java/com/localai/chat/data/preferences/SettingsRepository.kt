package com.localai.chat.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences ->
            AppSettings(
                apiBaseUrl = preferences[Keys.ApiBaseUrl] ?: AppSettings().apiBaseUrl,
                defaultModel = preferences[Keys.DefaultModel] ?: AppSettings().defaultModel,
                assistantDisplayName = preferences[Keys.AssistantDisplayName]
                    ?: AppSettings().assistantDisplayName,
                streamingEnabled = preferences[Keys.StreamingEnabled] ?: AppSettings().streamingEnabled,
                backgroundGenerationEnabled = preferences[Keys.BackgroundGenerationEnabled]
                    ?: AppSettings().backgroundGenerationEnabled,
                notificationsEnabled = preferences[Keys.NotificationsEnabled] ?: AppSettings().notificationsEnabled,
                defaultSystemPrompt = preferences[Keys.DefaultSystemPrompt] ?: AppSettings().defaultSystemPrompt,
                thinkingDisplayMode = preferences[Keys.ThinkingDisplayMode].toEnumOrDefault(
                    ThinkingDisplayMode.Collapsed,
                ),
                themeMode = preferences[Keys.ThemeMode].toEnumOrDefault(ThemeMode.System),
                colorPalette = preferences[Keys.ColorPalette].toEnumOrDefault(ColorPalette.Warm),
                chatFontSizePx = preferences[Keys.ChatFontSizePx] ?: AppSettings().chatFontSizePx,
            )
        }

    suspend fun saveConnectionSettings(
        apiBaseUrl: String,
        defaultModel: String,
        assistantDisplayName: String,
        defaultSystemPrompt: String,
        streamingEnabled: Boolean,
        thinkingDisplayMode: ThinkingDisplayMode,
        themeMode: ThemeMode,
        chatFontSizePx: Int,
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.ApiBaseUrl] = apiBaseUrl.trim()
            preferences[Keys.DefaultModel] = defaultModel.trim()
            preferences[Keys.AssistantDisplayName] = assistantDisplayName.trim().ifBlank {
                AppSettings().assistantDisplayName
            }
            preferences[Keys.DefaultSystemPrompt] = defaultSystemPrompt.trim()
            preferences[Keys.StreamingEnabled] = streamingEnabled
            preferences[Keys.ThinkingDisplayMode] = thinkingDisplayMode.name
            preferences[Keys.ThemeMode] = themeMode.name
            preferences[Keys.ChatFontSizePx] = chatFontSizePx.coerceIn(MinChatFontSizePx, MaxChatFontSizePx)
        }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T {
        return enumValues<T>().firstOrNull { it.name == this } ?: default
    }

    private object Keys {
        val ApiBaseUrl = stringPreferencesKey("api_base_url")
        val DefaultModel = stringPreferencesKey("default_model")
        val AssistantDisplayName = stringPreferencesKey("assistant_display_name")
        val StreamingEnabled = booleanPreferencesKey("streaming_enabled")
        val BackgroundGenerationEnabled = booleanPreferencesKey("background_generation_enabled")
        val NotificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val DefaultSystemPrompt = stringPreferencesKey("default_system_prompt")
        val ThinkingDisplayMode = stringPreferencesKey("thinking_display_mode")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val ColorPalette = stringPreferencesKey("color_palette")
        val ChatFontSizePx = intPreferencesKey("chat_font_size_px")
    }

    private companion object {
        const val MinChatFontSizePx = 12
        const val MaxChatFontSizePx = 32
    }
}
