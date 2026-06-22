package com.localai.chat.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.localai.chat.data.db.LocalAiDatabase
import com.localai.chat.data.db.dao.ChatDao
import com.localai.chat.data.db.dao.MessageDao
import com.localai.chat.data.secure.ApiKeyStore
import com.localai.chat.data.secure.EncryptedApiKeyStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): LocalAiDatabase = Room.databaseBuilder(
        context,
        LocalAiDatabase::class.java,
        "local_ai_chat.db",
    ).addMigrations(Migration1To2, Migration2To3).build()

    @Provides
    fun provideChatDao(database: LocalAiDatabase): ChatDao = database.chatDao()

    @Provides
    fun provideMessageDao(database: LocalAiDatabase): MessageDao = database.messageDao()

    private val Migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN generationDurationMs INTEGER")
        }
    }

    private val Migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN attachmentsJson TEXT")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SecureStorageModule {
    @Binds
    @Singleton
    abstract fun bindApiKeyStore(
        implementation: EncryptedApiKeyStore,
    ): ApiKeyStore
}
