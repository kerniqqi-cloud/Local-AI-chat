package com.localai.chat.data.secure

interface ApiKeyStore {
    suspend fun getApiKey(): String?

    suspend fun setApiKey(apiKey: String?)
}
