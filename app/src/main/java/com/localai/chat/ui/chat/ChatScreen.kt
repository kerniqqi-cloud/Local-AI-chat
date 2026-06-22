package com.localai.chat.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localai.chat.data.repository.Chat
import com.localai.chat.data.repository.ChatMessage
import com.localai.chat.data.repository.ImageAttachment
import com.localai.chat.data.repository.MessageRole
import com.localai.chat.data.repository.MessageStatus
import com.localai.chat.ui.thinking.AssistantMessageRenderer
import kotlinx.coroutines.launch

@Composable
fun ChatRoute(
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ChatScreen(
        uiState = uiState,
        onOpenSettings = onOpenSettings,
        onCreateChat = viewModel::createChat,
        onSelectChat = viewModel::selectChat,
        onDeleteChat = viewModel::deleteChat,
        onDeleteAllChats = viewModel::deleteAllChats,
        onStartRenameChat = viewModel::startRenameChat,
        onChatSearchQueryChanged = viewModel::onChatSearchQueryChanged,
        onRenameChatTitleChanged = viewModel::onRenameChatTitleChanged,
        onCancelRenameChat = viewModel::cancelRenameChat,
        onConfirmRenameChat = viewModel::confirmRenameChat,
        onInputChanged = viewModel::onInputChanged,
        onSendMessage = viewModel::sendMessage,
        onStopGeneration = viewModel::stopGeneration,
        onAddImageAttachment = viewModel::addImageAttachment,
        onCreateCameraCaptureUri = viewModel::createCameraCaptureUri,
        onCameraCaptureResult = viewModel::onCameraCaptureResult,
        onRemovePendingAttachment = viewModel::removePendingAttachment,
        onEditMessage = viewModel::startEditMessage,
        onEditingMessageTextChanged = viewModel::onEditingMessageTextChanged,
        onCancelEditMessage = viewModel::cancelEditMessage,
        onConfirmEditMessage = viewModel::confirmEditMessage,
        onRequestRegenerate = viewModel::requestRegenerate,
        onCancelRegenerate = viewModel::cancelRegenerate,
        onConfirmRegenerate = viewModel::confirmRegenerate,
        onDeleteMessage = viewModel::deleteMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    uiState: ChatUiState,
    onOpenSettings: () -> Unit,
    onCreateChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onDeleteAllChats: () -> Unit,
    onStartRenameChat: (Chat) -> Unit,
    onChatSearchQueryChanged: (String) -> Unit,
    onRenameChatTitleChanged: (String) -> Unit,
    onCancelRenameChat: () -> Unit,
    onConfirmRenameChat: () -> Unit,
    onInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onAddImageAttachment: (Uri) -> Unit,
    onCreateCameraCaptureUri: () -> Uri?,
    onCameraCaptureResult: (Boolean) -> Unit,
    onRemovePendingAttachment: (String) -> Unit,
    onEditMessage: (ChatMessage) -> Unit,
    onEditingMessageTextChanged: (String) -> Unit,
    onCancelEditMessage: () -> Unit,
    onConfirmEditMessage: () -> Unit,
    onRequestRegenerate: (String) -> Unit,
    onCancelRegenerate: () -> Unit,
    onConfirmRegenerate: () -> Unit,
    onDeleteMessage: (String) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onAddImageAttachment(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = onCameraCaptureResult,
    )
    val launchPhotoPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
    val launchCamera = {
        val uri = onCreateCameraCaptureUri()
        if (uri != null) cameraLauncher.launch(uri)
    }
    var expandedAttachment by remember { mutableStateOf<ImageAttachment?>(null) }
    var showDeleteAllChatsDialog by remember { mutableStateOf(false) }
    val isNearBottom by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems == 0 || lastVisible >= totalItems - 2
        }
    }
    val lastMessageSignal = uiState.messages.lastOrNull()?.id

    LaunchedEffect(lastMessageSignal) {
        if (uiState.messages.isNotEmpty() && isNearBottom) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawer(
                chats = uiState.chats,
                searchQuery = uiState.chatSearchQuery,
                searchResults = uiState.chatSearchResults,
                activeChatId = uiState.activeChatId,
                onCreateChat = {
                    onCreateChat()
                    coroutineScope.launch { drawerState.close() }
                },
                onSelectChat = { chatId ->
                    onSelectChat(chatId)
                    coroutineScope.launch { drawerState.close() }
                },
                onDeleteChat = onDeleteChat,
                onRenameChat = onStartRenameChat,
                onSearchQueryChanged = onChatSearchQueryChanged,
                onRequestDeleteAllChats = { showDeleteAllChatsDialog = true },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.activeChat?.title ?: "Local AI Chat",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open chats",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onCreateChat) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New chat",
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open settings",
                            )
                        }
                    },
                )
            },
            bottomBar = {
                ChatInputBar(
                    inputText = uiState.inputText,
                    pendingAttachments = uiState.pendingAttachments,
                    attachmentError = uiState.attachmentError,
                    isGenerating = uiState.isGenerating,
                    generationStatusText = uiState.generationStatusText,
                    onInputChanged = onInputChanged,
                    onSendMessage = onSendMessage,
                    onStopGeneration = onStopGeneration,
                    onPickImage = launchPhotoPicker,
                    onTakePhoto = launchCamera,
                    onRemoveAttachment = onRemovePendingAttachment,
                    onOpenAttachment = { expandedAttachment = it },
                )
            },
        ) { contentPadding ->
            if (uiState.messages.isEmpty()) {
                EmptyChatState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        reverseLayout = false,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    ) {
                        items(
                            items = uiState.messages,
                            key = { it.id },
                        ) { message ->
                            MessageCard(
                                message = message,
                                thinkingDisplayMode = uiState.thinkingDisplayMode,
                                assistantDisplayName = uiState.assistantDisplayName,
                                fontSizePx = uiState.chatFontSizePx,
                                onOpenAttachment = { expandedAttachment = it },
                                onEditMessage = onEditMessage,
                                onRegenerateMessage = onRequestRegenerate,
                                onDeleteMessage = onDeleteMessage,
                            )
                        }
                    }

                    if (!isNearBottom) {
                        FloatingActionButton(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(uiState.messages.lastIndex)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                            )
                        }
                    }
                }
            }
        }

        if (uiState.editingMessageId != null) {
            EditMessageDialog(
                text = uiState.editingMessageText,
                onTextChanged = onEditingMessageTextChanged,
                onDismiss = onCancelEditMessage,
                onConfirm = onConfirmEditMessage,
            )
        }

        if (uiState.pendingRegenerateMessageId != null) {
            RegenerateMessageDialog(
                onDismiss = onCancelRegenerate,
                onConfirm = onConfirmRegenerate,
            )
        }

        if (uiState.renamingChatId != null) {
            RenameChatDialog(
                title = uiState.renameChatTitle,
                onTitleChanged = onRenameChatTitleChanged,
                onDismiss = onCancelRenameChat,
                onConfirm = onConfirmRenameChat,
            )
        }

        expandedAttachment?.let { attachment ->
            ImagePreviewDialog(
                attachment = attachment,
                onDismiss = { expandedAttachment = null },
            )
        }

        if (showDeleteAllChatsDialog) {
            DeleteAllChatsDialog(
                onDismiss = { showDeleteAllChatsDialog = false },
                onConfirm = {
                    showDeleteAllChatsDialog = false
                    onDeleteAllChats()
                },
            )
        }
    }
}

@Composable
private fun ChatDrawer(
    chats: List<Chat>,
    searchQuery: String,
    searchResults: List<Chat>,
    activeChatId: String?,
    onCreateChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onRenameChat: (Chat) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onRequestDeleteAllChats: () -> Unit,
) {
    var searchVisible by remember { mutableStateOf(false) }
    val displayedChats = if (searchQuery.isBlank()) chats else searchResults

    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "Chats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { searchVisible = !searchVisible }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search chats",
                    )
                }
            }
            if (searchVisible) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text(text = "Search chats and messages") },
                    singleLine = true,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCreateChat,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "New chat")
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRequestDeleteAllChats,
                enabled = chats.isNotEmpty(),
            ) {
                Text(text = "Delete all chats")
            }
            HorizontalDivider()
            Text(
                text = if (searchQuery.isBlank()) "Recent chats" else "Search results",
                style = MaterialTheme.typography.titleMedium,
            )
            if (displayedChats.isEmpty()) {
                Text(
                    text = if (searchQuery.isBlank()) "No chats yet." else "No chats found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                displayedChats.forEach { chat ->
                    ChatDrawerRow(
                        chat = chat,
                        selected = chat.id == activeChatId,
                        onSelectChat = onSelectChat,
                        onDeleteChat = onDeleteChat,
                        onRenameChat = onRenameChat,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatDrawerRow(
    chat: Chat,
    selected: Boolean,
    onSelectChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onRenameChat: (Chat) -> Unit,
) {
    val colors = if (selected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = colors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectChat(chat.id) }
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = chat.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { onRenameChat(chat) }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename chat",
                )
            }
            IconButton(onClick = { onDeleteChat(chat.id) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete chat",
                )
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: ChatMessage,
    thinkingDisplayMode: com.localai.chat.data.preferences.ThinkingDisplayMode,
    assistantDisplayName: String,
    fontSizePx: Int,
    onOpenAttachment: (ImageAttachment) -> Unit,
    onEditMessage: (ChatMessage) -> Unit,
    onRegenerateMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val isUser = message.role == MessageRole.User
    val displayContent = if (isUser) message.content else message.rawContent
    val horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    val colors = if (message.status == MessageStatus.Failed) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else if (isUser) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .animateContentSize(),
            colors = colors,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = when {
                            message.status == MessageStatus.Failed -> "Error"
                            isUser -> "You"
                            else -> assistantDisplayName.ifBlank { "Assistant" }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        modifier = Modifier.size(32.dp),
                        onClick = { clipboardManager.setText(AnnotatedString(displayContent)) },
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                        )
                    }
                    if (isUser) {
                        IconButton(
                            modifier = Modifier.size(32.dp),
                            onClick = { onEditMessage(message) },
                            enabled = message.status == MessageStatus.Complete,
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit message",
                            )
                        }
                    } else {
                        IconButton(
                            modifier = Modifier.size(32.dp),
                            onClick = { onRegenerateMessage(message.id) },
                            enabled = message.status != MessageStatus.Streaming,
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate response",
                            )
                        }
                    }
                    IconButton(
                        modifier = Modifier.size(32.dp),
                        onClick = { onDeleteMessage(message.id) },
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete message",
                        )
                    }
                }
                if (isUser) {
                    if (message.attachments.isNotEmpty()) {
                        AttachmentPreviewRow(
                            attachments = message.attachments,
                            onRemoveAttachment = null,
                            onOpenAttachment = onOpenAttachment,
                        )
                    }
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = fontSizePx.coerceIn(12, 32).sp,
                            ),
                        )
                    }
                    if (message.content.isBlank() && message.attachments.isEmpty()) {
                        Text(
                            text = "Empty message",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    AssistantMessageRenderer(
                        messageId = message.id,
                        content = displayContent.ifBlank {
                            if (message.status == MessageStatus.Streaming) "Streaming response..." else ""
                        },
                        thinkingDisplayMode = thinkingDisplayMode,
                        fontSizePx = fontSizePx,
                    )
                }
                if (message.status == MessageStatus.Streaming) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (!isUser && message.generationDurationMs != null) {
                    Text(
                        text = "Took ${formatDuration(message.generationDurationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

@Composable
private fun EditMessageDialog(
    text: String,
    onTextChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Edit message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Saving this edit will remove later messages so the conversation state stays consistent.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = text,
                    onValueChange = onTextChanged,
                    minLines = 3,
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = text.isNotBlank(),
            ) {
                Text(text = "Save edit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun RegenerateMessageDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Regenerate response?") },
        text = {
            Text(
                text = "This will replace the selected assistant response and remove later messages. The request will include the full conversation context before that response.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "Regenerate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun DeleteAllChatsDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete all chats?") },
        text = {
            Text(
                text = "This removes all chat history from this device. This action cannot be undone from inside the app.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "Delete all")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun RenameChatDialog(
    title: String,
    onTitleChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Rename chat") },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = title,
                onValueChange = onTitleChanged,
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = title.isNotBlank(),
            ) {
                Text(text = "Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun AttachmentPreviewRow(
    attachments: List<ImageAttachment>,
    onRemoveAttachment: ((String) -> Unit)?,
    onOpenAttachment: (ImageAttachment) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            AttachmentThumbnail(
                attachment = attachment,
                onRemoveAttachment = onRemoveAttachment,
                onOpenAttachment = onOpenAttachment,
            )
        }
    }
}

@Composable
private fun AttachmentThumbnail(
    attachment: ImageAttachment,
    onRemoveAttachment: ((String) -> Unit)?,
    onOpenAttachment: (ImageAttachment) -> Unit,
) {
    val bitmap = remember(attachment.localPath) {
        decodeAttachmentBitmap(attachment.localPath, PreviewMaxPixels)
    }

    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenAttachment(attachment) },
    ) {
        if (bitmap != null) {
            Image(
                modifier = Modifier.fillMaxSize(),
                bitmap = bitmap,
                contentDescription = attachment.displayName,
                contentScale = ContentScale.Crop,
            )
        } else {
            Card(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = attachment.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (onRemoveAttachment != null) {
            IconButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp),
                onClick = { onRemoveAttachment(attachment.id) },
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove attachment",
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    attachment: ImageAttachment,
    onDismiss: () -> Unit,
) {
    val bitmap = remember(attachment.localPath) {
        decodeAttachmentBitmap(attachment.localPath, ExpandedImageMaxPixels)
    }
    var scale by remember(attachment.id) { mutableStateOf(1f) }
    var offsetX by remember(attachment.id) { mutableStateOf(0f) }
    var offsetY by remember(attachment.id) { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale == 1f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += panChange.x
            offsetY += panChange.y
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f)),
        ) {
            if (bitmap != null) {
                Image(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        )
                        .transformable(transformState),
                    bitmap = bitmap,
                    contentDescription = attachment.displayName,
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = "Unable to preview image.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            IconButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                onClick = onDismiss,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close image preview",
                    tint = Color.White,
                )
            }
        }
    }
}

private fun decodeAttachmentBitmap(
    localPath: String,
    maxPixels: Int,
) = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(localPath, bounds)
    val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxPixels)
    val decoded = BitmapFactory.decodeFile(
        localPath,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    ) ?: return@runCatching null
    val oriented = decoded.applyExifOrientation(localPath)
    if (oriented !== decoded) decoded.recycle()
    oriented.asImageBitmap()
}.getOrNull()

private fun Bitmap.applyExifOrientation(localPath: String): Bitmap {
    val matrix = runCatching {
        val exif = ExifInterface(localPath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        orientationMatrix(orientation)
    }.getOrNull() ?: return this

    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun orientationMatrix(orientation: Int): Matrix? {
    if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
        return null
    }

    return Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(270f)
                postScale(-1f, 1f)
            }
        }
    }
}

private fun calculateSampleSize(
    width: Int,
    height: Int,
    maxPixels: Int,
): Int {
    if (width <= 0 || height <= 0) return 1
    var sampleSize = 1
    while (width / sampleSize > maxPixels || height / sampleSize > maxPixels) {
        sampleSize *= 2
    }
    return sampleSize
}

private const val PreviewMaxPixels = 512
private const val ExpandedImageMaxPixels = 2048

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Start a conversation",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Messages are stored locally on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    pendingAttachments: List<ImageAttachment>,
    attachmentError: String?,
    isGenerating: Boolean,
    generationStatusText: String?,
    onInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenAttachment: (ImageAttachment) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (pendingAttachments.isNotEmpty()) {
            AttachmentPreviewRow(
                attachments = pendingAttachments,
                onRemoveAttachment = onRemoveAttachment,
                onOpenAttachment = onOpenAttachment,
            )
        }
        if (!attachmentError.isNullOrBlank()) {
            Text(
                text = attachmentError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AttachmentPickerButton(
                enabled = !isGenerating,
                onPickImage = onPickImage,
                onTakePhoto = onTakePhoto,
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = { Text(text = "Send message") },
                minLines = 1,
                maxLines = 5,
            )
            if (isGenerating) {
                IconButton(
                    modifier = Modifier.size(56.dp),
                    onClick = onStopGeneration,
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop generation",
                    )
                }
            } else {
                IconButton(
                    modifier = Modifier.size(56.dp),
                    onClick = onSendMessage,
                    enabled = inputText.isNotBlank() || pendingAttachments.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                    )
                }
            }
        }
        Text(
            text = if (isGenerating) {
                generationStatusText ?: "Generating response..."
            } else {
                "Full context sent every turn. No truncation."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun AttachmentPickerButton(
    enabled: Boolean,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            modifier = Modifier.size(56.dp),
            onClick = { expanded = true },
            enabled = enabled,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add image",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = "Gallery image") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    onPickImage()
                },
            )
            DropdownMenuItem(
                text = { Text(text = "Camera photo") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    onTakePhoto()
                },
            )
        }
    }
}
