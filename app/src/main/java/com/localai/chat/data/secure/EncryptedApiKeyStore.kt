package com.localai.chat.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class EncryptedApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ApiKeyStore {
    private val preferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            SecurePrefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        preferences.getString(ApiKey, null)
    }

    override suspend fun setApiKey(apiKey: String?) {
        withContext(Dispatchers.IO) {
            preferences.edit().apply {
                if (apiKey.isNullOrBlank()) remove(ApiKey) else putString(ApiKey, apiKey.trim())
            }.apply()
        }
    }

    private companion object {
        const val SecurePrefsName = "secure_prefs"
        const val ApiKey = "api_key"
    }
}
