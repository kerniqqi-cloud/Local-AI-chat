package com.localai.chat.ui.chat

import com.localai.chat.data.repository.Chat
import com.localai.chat.data.repository.ChatMessage
import com.localai.chat.data.repository.ImageAttachment
import com.localai.chat.data.preferences.ThinkingDisplayMode

data class ChatUiState(
    val chats: List<Chat> = emptyList(),
    val activeChatId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val generationStatusText: String? = null,
    val thinkingDisplayMode: ThinkingDisplayMode = ThinkingDisplayMode.Collapsed,
    val assistantDisplayName: String = "Assistant",
    val chatFontSizePx: Int = 18,
    val chatSearchQuery: String = "",
    val chatSearchResults: List<Chat> = emptyList(),
    val editingMessageId: String? = null,
    val editingMessageText: String = "",
    val pendingRegenerateMessageId: String? = null,
    val renamingChatId: String? = null,
    val renameChatTitle: String = "",
    val pendingAttachments: List<ImageAttachment> = emptyList(),
    val attachmentError: String? = null,
    val isLoading: Boolean = true,
) {
    val activeChat: Chat? = chats.firstOrNull { it.id == activeChatId }
}
