package com.localai.chat.data.preferences

import com.localai.chat.core.config.AppDefaults

data class AppSettings(
    val apiBaseUrl: String = AppDefaults.ApiBaseUrl,
    val defaultModel: String = "",
    val assistantDisplayName: String = AppDefaults.AssistantDisplayName,
    val streamingEnabled: Boolean = true,
    val backgroundGenerationEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val defaultSystemPrompt: String = "",
    val thinkingDisplayMode: ThinkingDisplayMode = ThinkingDisplayMode.Collapsed,
    val themeMode: ThemeMode = ThemeMode.System,
    val colorPalette: ColorPalette = ColorPalette.Warm,
    val chatFontSizePx: Int = 18,
)

enum class ThinkingDisplayMode {
    Hidden,
    Collapsed,
    Expanded,
}

enum class ThemeMode {
    Light,
    Dark,
    System,
}

enum class ColorPalette {
    Warm,
    Blue,
    Green,
    Purple,
    Neutral,
    Dynamic,
}
