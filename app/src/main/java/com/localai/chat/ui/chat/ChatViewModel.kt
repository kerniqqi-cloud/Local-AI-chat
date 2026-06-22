package com.localai.chat.ui.chat

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localai.chat.core.error.AppError
import com.localai.chat.data.preferences.AppSettings
import com.localai.chat.data.preferences.SettingsRepository
import com.localai.chat.data.repository.Chat
import com.localai.chat.data.repository.ChatMessage
import com.localai.chat.data.repository.ChatRepository
import com.localai.chat.data.repository.ImageAttachment
import com.localai.chat.data.repository.ImageAttachmentStore
import com.localai.chat.data.repository.MessageStatus
import com.localai.chat.data.repository.PendingCameraCapture
import com.localai.chat.data.secure.ApiKeyStore
import com.localai.chat.domain.context.ChatRequestBuilder
import com.localai.chat.domain.context.OpenAiChatMessage
import com.localai.chat.network.openai.ApiResult
import com.localai.chat.network.openai.ChatCompletionRequestDto
import com.localai.chat.network.openai.ChatCompletionRequestMessageDto
import com.localai.chat.network.openai.ChatCompletionResult
import com.localai.chat.network.openai.ChatStreamEvent
import com.localai.chat.network.openai.OpenAiApiClient
import com.localai.chat.service.generation.GenerationForegroundServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val openAiApiClient: OpenAiApiClient,
    private val chatRequestBuilder: ChatRequestBuilder,
    private val foregroundServiceController: GenerationForegroundServiceController,
    private val imageAttachmentStore: ImageAttachmentStore,
) : ViewModel() {
    private val activeChatId = MutableStateFlow<String?>(null)
    private val chatSearchQuery = MutableStateFlow("")
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var generationJob: Job? = null
    private var pendingCameraCapture: PendingCameraCapture? = null

    init {
        viewModelScope.launch {
            chatRepository.markStaleStreamingMessagesInterrupted()
        }

        viewModelScope.launch {
            val initialChatId = chatRepository.getOrCreateFreshChat()
            activeChatId.value = initialChatId
            _uiState.update { it.copy(activeChatId = initialChatId, isLoading = false) }
        }

        viewModelScope.launch {
            chatRepository.observeRecentChats().collect { chats ->
                _uiState.update { current -> current.copy(chats = chats) }
            }
        }

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        thinkingDisplayMode = settings.thinkingDisplayMode,
                        assistantDisplayName = settings.assistantDisplayName,
                        chatFontSizePx = settings.chatFontSizePx,
                    )
                }
            }
        }

        viewModelScope.launch {
            chatSearchQuery
                .flatMapLatest { query -> chatRepository.observeChatSearch(query) }
                .collect { results ->
                    _uiState.update { current -> current.copy(chatSearchResults = results) }
                }
        }

        viewModelScope.launch {
            activeChatId
                .filterNotNull()
                .flatMapLatest(chatRepository::observeMessages)
                .collect { messages ->
                    _uiState.update { current -> current.copy(messages = messages) }
                }
        }
    }

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun selectChat(chatId: String) {
        activeChatId.value = chatId
        _uiState.update { it.copy(activeChatId = chatId, messages = emptyList()) }
    }

    fun createChat() {
        viewModelScope.launch {
            val chatId = chatRepository.createChat()
            selectChat(chatId)
        }
    }

    fun onChatSearchQueryChanged(value: String) {
        chatSearchQuery.value = value
        _uiState.update { it.copy(chatSearchQuery = value) }
    }

    fun deleteAllChats() {
        viewModelScope.launch {
            chatRepository.deleteAllChats()
            val replacementChatId = chatRepository.getOrCreateFreshChat()
            selectChat(replacementChatId)
            onChatSearchQueryChanged("")
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.deleteChat(chatId)
            if (_uiState.value.activeChatId == chatId) {
                val replacementChatId = chatRepository.getOrCreateFreshChat()
                selectChat(replacementChatId)
            }
        }
    }

    fun startRenameChat(chat: Chat) {
        _uiState.update {
            it.copy(
                renamingChatId = chat.id,
                renameChatTitle = chat.title,
            )
        }
    }

    fun onRenameChatTitleChanged(value: String) {
        _uiState.update { it.copy(renameChatTitle = value) }
    }

    fun cancelRenameChat() {
        _uiState.update { it.copy(renamingChatId = null, renameChatTitle = "") }
    }

    fun confirmRenameChat() {
        val state = _uiState.value
        val chatId = state.renamingChatId ?: return
        val title = state.renameChatTitle
        if (title.isBlank()) return

        viewModelScope.launch {
            chatRepository.renameChat(chatId, title)
            _uiState.update { it.copy(renamingChatId = null, renameChatTitle = "") }
        }
    }

    fun addImageAttachment(uri: Uri) {
        if (_uiState.value.isGenerating) return
        viewModelScope.launch {
            runCatching { imageAttachmentStore.copyFromUri(uri) }
                .onSuccess { attachment ->
                    _uiState.update { state ->
                        state.copy(
                            pendingAttachments = state.pendingAttachments + attachment,
                            attachmentError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(attachmentError = error.message ?: "Unable to attach image.") }
                }
        }
    }

    fun createCameraCaptureUri(): Uri? {
        if (_uiState.value.isGenerating) return null
        return runCatching {
            imageAttachmentStore.createCameraCaptureTarget().also { pendingCameraCapture = it }.uri
        }.onFailure { error ->
            _uiState.update { it.copy(attachmentError = error.message ?: "Unable to start camera.") }
        }.getOrNull()
    }

    fun onCameraCaptureResult(success: Boolean) {
        val target = pendingCameraCapture ?: return
        pendingCameraCapture = null
        if (!success) {
            viewModelScope.launch { imageAttachmentStore.delete(target.localPath) }
            return
        }

        viewModelScope.launch {
            runCatching { imageAttachmentStore.completeCameraCapture(target) }
                .onSuccess { attachment ->
                    _uiState.update { state ->
                        state.copy(
                            pendingAttachments = state.pendingAttachments + attachment,
                            attachmentError = null,
                        )
                    }
                }
                .onFailure { error ->
                    imageAttachmentStore.delete(target.localPath)
                    _uiState.update { it.copy(attachmentError = error.message ?: "Unable to save camera image.") }
                }
        }
    }

    fun removePendingAttachment(attachmentId: String) {
        val attachment = _uiState.value.pendingAttachments.firstOrNull { it.id == attachmentId }
        _uiState.update { state ->
            state.copy(pendingAttachments = state.pendingAttachments.filterNot { it.id == attachmentId })
        }
        if (attachment != null) {
            viewModelScope.launch { imageAttachmentStore.delete(attachment.localPath) }
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val chatId = state.activeChatId ?: return
        val content = state.inputText
        val attachments = state.pendingAttachments
        if ((content.isBlank() && attachments.isEmpty()) || state.isGenerating) return

        generationJob = viewModelScope.launch {
            val streamedContent = StringBuilder()
            val reasoningContent = StringBuilder()
            var assistantMessageId: String? = null
            var foregroundStarted = false
            var foregroundFinalized = false
            var notificationChatTitle = "Current chat"
            var generationStartedAt: Long? = null
            try {
                _uiState.update {
                    it.copy(
                        inputText = "",
                        pendingAttachments = emptyList(),
                        attachmentError = null,
                        isGenerating = true,
                        generationStatusText = "Preparing request...",
                    )
                }
                chatRepository.addUserMessage(chatId, content, attachments)

                val settings = settingsRepository.settings.first()
                val model = settings.defaultModel.trim()
                if (model.isEmpty()) {
                    chatRepository.addAssistantMessage(
                        chatId = chatId,
                        content = "Choose a model in Settings before sending a request.",
                        status = MessageStatus.Failed,
                        errorMessage = "Missing model",
                    )
                    return@launch
                }

                notificationChatTitle = notificationTitle(content.ifBlank { attachments.firstOrNull()?.displayName ?: "Image request" })
                foregroundStarted = foregroundServiceController.start(notificationChatTitle, model)

                val request = buildRequest(chatId, settings, model)
                val apiKey = runCatching { apiKeyStore.getApiKey() }.getOrNull()
                val startedAt = SystemClock.elapsedRealtime()
                generationStartedAt = startedAt

                if (settings.streamingEnabled) {
                    assistantMessageId = chatRepository.addAssistantMessage(
                        chatId = chatId,
                        content = "",
                        status = MessageStatus.Streaming,
                    )
                    val streamFailure = streamIntoMessage(
                        settings = settings,
                        apiKey = apiKey,
                        request = request,
                        assistantMessageId = assistantMessageId,
                        streamedContent = streamedContent,
                        reasoningContent = reasoningContent,
                        startedAt = startedAt,
                    )

                    if (streamFailure == null) {
                        val finalContent = streamedContent.toString()
                        val success = finalContent.isNotBlank()
                        chatRepository.updateAssistantMessage(
                            messageId = assistantMessageId,
                            content = finalContent.ifBlank { "The model returned an empty response." },
                            rawContent = buildRawAssistantContent(reasoningContent, finalContent.ifBlank { "The model returned an empty response." }),
                            status = if (success) MessageStatus.Complete else MessageStatus.Failed,
                            errorMessage = if (finalContent.isBlank()) "Empty model response" else null,
                            generationDurationMs = elapsedMs(startedAt),
                        )
                        if (foregroundStarted) {
                            if (success) {
                                foregroundServiceController.complete(notificationChatTitle)
                            } else {
                                foregroundServiceController.failure("The model returned an empty response.")
                            }
                            foregroundFinalized = true
                        }
                    } else if (streamedContent.isBlank() && shouldFallbackToNonStreaming(streamFailure)) {
                        _uiState.update { it.copy(generationStatusText = "Streaming failed; retrying without streaming...") }
                        val result = openAiApiClient.createChatCompletion(
                            baseUrl = settings.apiBaseUrl,
                            apiKey = apiKey,
                            request = request.copy(stream = false),
                        )
                        val success = persistNonStreamingResult(
                            chatId = chatId,
                            assistantMessageId = assistantMessageId,
                            result = result,
                            generationDurationMs = elapsedMs(startedAt),
                        )
                        if (foregroundStarted) {
                            if (success) {
                                foregroundServiceController.complete(notificationChatTitle)
                            } else {
                                foregroundServiceController.failure(result.failureMessage())
                            }
                            foregroundFinalized = true
                        }
                    } else {
                        chatRepository.updateAssistantMessage(
                            messageId = assistantMessageId,
                            content = streamedContent.toString().ifBlank { streamFailure.userMessage },
                            rawContent = buildRawAssistantContent(
                                reasoningContent,
                                streamedContent.toString().ifBlank { streamFailure.userMessage },
                            ),
                            status = if (streamedContent.isBlank()) MessageStatus.Failed else MessageStatus.Interrupted,
                            errorMessage = streamFailure.userMessage,
                            generationDurationMs = elapsedMs(startedAt),
                        )
                        if (foregroundStarted) {
                            foregroundServiceController.failure(streamFailure.userMessage)
                            foregroundFinalized = true
                        }
                    }
                } else {
                    val result = openAiApiClient.createChatCompletion(
                        baseUrl = settings.apiBaseUrl,
                        apiKey = apiKey,
                        request = request.copy(stream = false),
                    )
                    val success = persistNonStreamingResult(
                        chatId = chatId,
                        assistantMessageId = null,
                        result = result,
                        generationDurationMs = elapsedMs(startedAt),
                    )
                    if (foregroundStarted) {
                        if (success) {
                            foregroundServiceController.complete(notificationChatTitle)
                        } else {
                            foregroundServiceController.failure(result.failureMessage())
                        }
                        foregroundFinalized = true
                    }
                }
            } catch (exception: CancellationException) {
                assistantMessageId?.let { messageId ->
                    chatRepository.updateAssistantMessage(
                        messageId = messageId,
                        content = streamedContent.toString().ifBlank { "Generation stopped." },
                        rawContent = buildRawAssistantContent(
                            reasoningContent,
                            streamedContent.toString().ifBlank { "Generation stopped." },
                        ),
                        status = MessageStatus.Cancelled,
                        errorMessage = "Cancelled by user",
                        generationDurationMs = generationStartedAt?.let(::elapsedMs),
                    )
                }
                if (foregroundStarted) {
                    foregroundServiceController.stop()
                    foregroundFinalized = true
                }
            } catch (exception: Exception) {
                val message = "Generation failed."
                if (assistantMessageId == null) {
                    chatRepository.addAssistantMessage(
                        chatId = chatId,
                        content = message,
                        status = MessageStatus.Failed,
                        errorMessage = exception.message,
                    )
                } else {
                    chatRepository.updateAssistantMessage(
                        messageId = assistantMessageId,
                        content = streamedContent.toString().ifBlank { message },
                        rawContent = buildRawAssistantContent(
                            reasoningContent,
                            streamedContent.toString().ifBlank { message },
                        ),
                        status = if (streamedContent.isBlank()) MessageStatus.Failed else MessageStatus.Interrupted,
                        errorMessage = exception.message,
                        generationDurationMs = generationStartedAt?.let(::elapsedMs),
                    )
                }
                if (foregroundStarted) {
                    foregroundServiceController.failure(exception.message ?: message)
                    foregroundFinalized = true
                }
            } finally {
                if (foregroundStarted && !foregroundFinalized) {
                    foregroundServiceController.stop()
                }
                _uiState.update { it.copy(isGenerating = false, generationStatusText = null) }
                generationJob = null
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
    }

    fun startEditMessage(message: ChatMessage) {
        if (message.role != com.localai.chat.data.repository.MessageRole.User || _uiState.value.isGenerating) return
        _uiState.update {
            it.copy(
                editingMessageId = message.id,
                editingMessageText = message.content,
            )
        }
    }

    fun onEditingMessageTextChanged(value: String) {
        _uiState.update { it.copy(editingMessageText = value) }
    }

    fun cancelEditMessage() {
        _uiState.update { it.copy(editingMessageId = null, editingMessageText = "") }
    }

    fun confirmEditMessage() {
        val state = _uiState.value
        val messageId = state.editingMessageId ?: return
        val newContent = state.editingMessageText
        if (newContent.isBlank() || state.isGenerating) return

        viewModelScope.launch {
            chatRepository.editUserMessage(messageId, newContent)
            _uiState.update { it.copy(editingMessageId = null, editingMessageText = "") }
        }
    }

    fun requestRegenerate(messageId: String) {
        if (_uiState.value.isGenerating) return
        _uiState.update { it.copy(pendingRegenerateMessageId = messageId) }
    }

    fun cancelRegenerate() {
        _uiState.update { it.copy(pendingRegenerateMessageId = null) }
    }

    fun confirmRegenerate() {
        val messageId = _uiState.value.pendingRegenerateMessageId ?: return
        if (_uiState.value.isGenerating) return

        generationJob = viewModelScope.launch {
            val streamedContent = StringBuilder()
            val reasoningContent = StringBuilder()
            var foregroundStarted = false
            var foregroundFinalized = false
            var notificationChatTitle = "Current chat"
            var generationStartedAt: Long? = null
            try {
                _uiState.update {
                    it.copy(
                        pendingRegenerateMessageId = null,
                        isGenerating = true,
                        generationStatusText = "Preparing regeneration...",
                    )
                }

                val target = chatRepository.prepareAssistantRegeneration(messageId) ?: return@launch
                val settings = settingsRepository.settings.first()
                val model = settings.defaultModel.trim()
                if (model.isEmpty()) {
                    chatRepository.updateAssistantMessage(
                        messageId = messageId,
                        content = "Choose a model in Settings before regenerating.",
                        status = MessageStatus.Failed,
                        errorMessage = "Missing model",
                    )
                    return@launch
                }

                notificationChatTitle = _uiState.value.activeChat?.title?.takeIf { it != "New chat" } ?: "Regenerating response"
                foregroundStarted = foregroundServiceController.start(notificationChatTitle, model)

                val request = buildRequest(
                    chatId = target.chatId,
                    settings = settings,
                    model = model,
                    stopBeforeMessageId = messageId,
                )
                val apiKey = runCatching { apiKeyStore.getApiKey() }.getOrNull()
                val startedAt = SystemClock.elapsedRealtime()
                generationStartedAt = startedAt

                if (settings.streamingEnabled) {
                    val streamFailure = streamIntoMessage(
                        settings = settings,
                        apiKey = apiKey,
                        request = request,
                        assistantMessageId = messageId,
                        streamedContent = streamedContent,
                        reasoningContent = reasoningContent,
                        startedAt = startedAt,
                    )

                    if (streamFailure == null) {
                        val finalContent = streamedContent.toString()
                        val success = finalContent.isNotBlank()
                        chatRepository.updateAssistantMessage(
                            messageId = messageId,
                            content = finalContent.ifBlank { "The model returned an empty response." },
                            rawContent = buildRawAssistantContent(reasoningContent, finalContent.ifBlank { "The model returned an empty response." }),
                            status = if (success) MessageStatus.Complete else MessageStatus.Failed,
                            errorMessage = if (success) null else "Empty model response",
                            generationDurationMs = elapsedMs(startedAt),
                        )
                        if (foregroundStarted) {
                            if (success) foregroundServiceController.complete(notificationChatTitle) else foregroundServiceController.failure("The model returned an empty response.")
                            foregroundFinalized = true
                        }
                    } else if (streamedContent.isBlank() && shouldFallbackToNonStreaming(streamFailure)) {
                        val result = openAiApiClient.createChatCompletion(
                            baseUrl = settings.apiBaseUrl,
                            apiKey = apiKey,
                            request = request.copy(stream = false),
                        )
                        val success = persistNonStreamingResult(
                            chatId = target.chatId,
                            assistantMessageId = messageId,
                            result = result,
                            generationDurationMs = elapsedMs(startedAt),
                        )
                        if (foregroundStarted) {
                            if (success) foregroundServiceController.complete(notificationChatTitle) else foregroundServiceController.failure(result.failureMessage())
                            foregroundFinalized = true
                        }
                    } else {
                        chatRepository.updateAssistantMessage(
                            messageId = messageId,
                            content = streamedContent.toString().ifBlank { streamFailure.userMessage },
                            rawContent = buildRawAssistantContent(
                                reasoningContent,
                                streamedContent.toString().ifBlank { streamFailure.userMessage },
                            ),
                            status = if (streamedContent.isBlank()) MessageStatus.Failed else MessageStatus.Interrupted,
                            errorMessage = streamFailure.userMessage,
                            generationDurationMs = elapsedMs(startedAt),
                        )
                        if (foregroundStarted) {
                            foregroundServiceController.failure(streamFailure.userMessage)
                            foregroundFinalized = true
                        }
                    }
                } else {
                    val result = openAiApiClient.createChatCompletion(
                        baseUrl = settings.apiBaseUrl,
                        apiKey = apiKey,
                        request = request.copy(stream = false),
                    )
                    val success = persistNonStreamingResult(
                        chatId = target.chatId,
                        assistantMessageId = messageId,
                        result = result,
                        generationDurationMs = elapsedMs(startedAt),
                    )
                    if (foregroundStarted) {
                        if (success) foregroundServiceController.complete(notificationChatTitle) else foregroundServiceController.failure(result.failureMessage())
                        foregroundFinalized = true
                    }
                }
            } catch (exception: CancellationException) {
                chatRepository.updateAssistantMessage(
                    messageId = messageId,
                    content = streamedContent.toString().ifBlank { "Generation stopped." },
                    rawContent = buildRawAssistantContent(
                        reasoningContent,
                        streamedContent.toString().ifBlank { "Generation stopped." },
                    ),
                    status = MessageStatus.Cancelled,
                    errorMessage = "Cancelled by user",
                    generationDurationMs = generationStartedAt?.let(::elapsedMs),
                )
                if (foregroundStarted) {
                    foregroundServiceController.stop()
                    foregroundFinalized = true
                }
            } catch (exception: Exception) {
                chatRepository.updateAssistantMessage(
                    messageId = messageId,
                    content = streamedContent.toString().ifBlank { "Generation failed." },
                    rawContent = buildRawAssistantContent(
                        reasoningContent,
                        streamedContent.toString().ifBlank { "Generation failed." },
                    ),
                    status = if (streamedContent.isBlank()) MessageStatus.Failed else MessageStatus.Interrupted,
                    errorMessage = exception.message,
                    generationDurationMs = generationStartedAt?.let(::elapsedMs),
                )
                if (foregroundStarted) {
                    foregroundServiceController.failure(exception.message ?: "Generation failed.")
                    foregroundFinalized = true
                }
            } finally {
                if (foregroundStarted && !foregroundFinalized) foregroundServiceController.stop()
                _uiState.update { it.copy(isGenerating = false, generationStatusText = null) }
                generationJob = null
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
        }
    }

    private suspend fun buildRequest(
        chatId: String,
        settings: AppSettings,
        model: String,
        stopBeforeMessageId: String? = null,
    ): ChatCompletionRequestDto {
        val requestContext = chatRequestBuilder.build(
            systemPrompt = settings.defaultSystemPrompt,
            messages = chatRepository.getMessagesForRequest(chatId),
            stopBeforeMessageId = stopBeforeMessageId,
        )
        return ChatCompletionRequestDto(
            model = model,
            messages = withContext(Dispatchers.IO) { requestContext.messages.map { it.toRequestDto() } },
            stream = settings.streamingEnabled,
        )
    }

    private fun OpenAiChatMessage.toRequestDto(): ChatCompletionRequestMessageDto {
        return ChatCompletionRequestMessageDto(
            role = role,
            content = toRequestContent(),
        )
    }

    private fun OpenAiChatMessage.toRequestContent(): JsonElement {
        if (attachments.isEmpty()) return JsonPrimitive(content)

        val parts = buildList<JsonElement> {
            if (content.isNotBlank()) {
                add(
                    buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(content))
                    },
                )
            }
            attachments.mapNotNull(::attachmentToDataUrl).forEach { dataUrl ->
                add(
                    buildJsonObject {
                        put("type", JsonPrimitive("image_url"))
                        put(
                            "image_url",
                            buildJsonObject { put("url", JsonPrimitive(dataUrl)) },
                        )
                    },
                )
            }
        }

        return if (parts.isEmpty()) JsonPrimitive(content.ifBlank { "[Image attachment unavailable]" }) else JsonArray(parts)
    }

    private fun attachmentToDataUrl(attachment: ImageAttachment): String? {
        return runCatching {
            val bytes = File(attachment.localPath).readBytes()
            "data:${attachment.mimeType};base64,${Base64.getEncoder().encodeToString(bytes)}"
        }.getOrNull()
    }

    private suspend fun streamIntoMessage(
        settings: AppSettings,
        apiKey: String?,
        request: ChatCompletionRequestDto,
        assistantMessageId: String,
        streamedContent: StringBuilder,
        reasoningContent: StringBuilder,
        startedAt: Long,
    ): AppError? {
        var streamFailure: AppError? = null
        var lastPersistAt = startedAt
        var lastElapsedUpdateAt = startedAt

        openAiApiClient.streamChatCompletion(settings.apiBaseUrl, apiKey, request).collect { result ->
            when (result) {
                is ApiResult.Success -> {
                    when (val event = result.value) {
                        is ChatStreamEvent.ContentDelta -> {
                            event.reasoningContent?.let(reasoningContent::append)
                            streamedContent.append(event.content)
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastPersistAt >= PartialPersistIntervalMs) {
                                chatRepository.updateAssistantMessage(
                                    messageId = assistantMessageId,
                                    content = streamedContent.toString(),
                                    rawContent = buildRawAssistantContent(reasoningContent, streamedContent.toString()),
                                    status = MessageStatus.Streaming,
                                )
                                lastPersistAt = now
                            }
                            if (now - lastElapsedUpdateAt >= ElapsedUpdateIntervalMs) {
                                _uiState.update {
                                    it.copy(generationStatusText = buildElapsedText(startedAt, now))
                                }
                                lastElapsedUpdateAt = now
                            }
                        }
                        is ChatStreamEvent.ReasoningDelta -> {
                            reasoningContent.append(event.content)
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastPersistAt >= PartialPersistIntervalMs) {
                                chatRepository.updateAssistantMessage(
                                    messageId = assistantMessageId,
                                    content = streamedContent.toString(),
                                    rawContent = buildRawAssistantContent(reasoningContent, streamedContent.toString()),
                                    status = MessageStatus.Streaming,
                                )
                                lastPersistAt = now
                            }
                            if (now - lastElapsedUpdateAt >= ElapsedUpdateIntervalMs) {
                                _uiState.update {
                                    it.copy(generationStatusText = buildElapsedText(startedAt, now))
                                }
                                lastElapsedUpdateAt = now
                            }
                        }
                        ChatStreamEvent.Done -> Unit
                        ChatStreamEvent.Empty -> Unit
                        is ChatStreamEvent.Usage -> Unit
                    }
                }
                is ApiResult.Failure -> streamFailure = result.error
            }
        }

        if (streamedContent.isNotEmpty() || reasoningContent.isNotEmpty()) {
            chatRepository.updateAssistantMessage(
                messageId = assistantMessageId,
                content = streamedContent.toString(),
                rawContent = buildRawAssistantContent(reasoningContent, streamedContent.toString()),
                status = MessageStatus.Streaming,
            )
        }

        return streamFailure
    }

    private suspend fun persistNonStreamingResult(
        chatId: String,
        assistantMessageId: String?,
        result: ApiResult<ChatCompletionResult>,
        generationDurationMs: Long,
    ): Boolean {
        when (result) {
            is ApiResult.Success -> {
                if (assistantMessageId == null) {
                    chatRepository.addAssistantMessage(
                        chatId = chatId,
                        content = result.value.content,
                        rawContent = result.value.rawContent,
                        generationDurationMs = generationDurationMs,
                    )
                } else {
                    chatRepository.updateAssistantMessage(
                        messageId = assistantMessageId,
                        content = result.value.content,
                        rawContent = result.value.rawContent,
                        status = MessageStatus.Complete,
                        generationDurationMs = generationDurationMs,
                    )
                }
                return true
            }
            is ApiResult.Failure -> {
                if (assistantMessageId == null) {
                    chatRepository.addAssistantMessage(
                        chatId = chatId,
                        content = result.error.userMessage,
                        status = MessageStatus.Failed,
                        errorMessage = result.error.userMessage,
                        generationDurationMs = generationDurationMs,
                    )
                } else {
                    chatRepository.updateAssistantMessage(
                        messageId = assistantMessageId,
                        content = result.error.userMessage,
                        status = MessageStatus.Failed,
                        errorMessage = result.error.userMessage,
                        generationDurationMs = generationDurationMs,
                    )
                }
                return false
            }
        }
    }

    private fun ApiResult<ChatCompletionResult>.failureMessage(): String {
        return (this as? ApiResult.Failure)?.error?.userMessage ?: "Generation failed."
    }

    private fun buildRawAssistantContent(
        reasoningContent: StringBuilder,
        finalContent: String,
    ): String = buildRawAssistantContent(reasoningContent.toString(), finalContent)

    private fun buildRawAssistantContent(
        reasoningContent: String,
        finalContent: String,
    ): String {
        val reasoning = reasoningContent.trim()
        if (reasoning.isBlank()) return finalContent

        return buildString {
            append("<think>")
            append(reasoning)
            append("</think>")
            if (finalContent.isNotBlank()) {
                append("\n\n")
                append(finalContent.trimStart())
            }
        }
    }

    private fun notificationTitle(firstUserMessage: String): String {
        val activeTitle = _uiState.value.activeChat?.title
            ?.takeIf { it.isNotBlank() && it != "New chat" }
        if (activeTitle != null) return activeTitle

        val normalized = firstUserMessage.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= NotificationTitleLength) {
            normalized.ifBlank { "Current chat" }
        } else {
            normalized.take(NotificationTitleLength).trim() + "..."
        }
    }

    private fun shouldFallbackToNonStreaming(error: AppError): Boolean {
        return error is AppError.Http || error is AppError.InvalidResponse || error is AppError.EmptyResponse
    }

    private fun buildElapsedText(
        startedAt: Long,
        now: Long,
    ): String {
        return "Generating response... ${formatDuration((now - startedAt).coerceAtLeast(0L))}"
    }

    private fun elapsedMs(startedAt: Long): Long {
        return (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    private companion object {
        const val PartialPersistIntervalMs = 350L
        const val ElapsedUpdateIntervalMs = 1_000L
        const val NotificationTitleLength = 56
    }
}
