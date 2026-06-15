package com.seina.chan.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.seina.chan.data.local.AppDatabase
import com.seina.chan.data.local.dao.MessageDao
import com.seina.chan.data.local.dao.SentImageDao
import com.seina.chan.data.remote.HermesWsClient
import com.seina.chan.data.repository.ChatRepository
import com.seina.chan.data.repository.ConnectionRepository
import com.seina.chan.data.repository.SessionRepository
import com.seina.chan.data.repository.SettingsRepository
import com.seina.chan.util.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(CIO) {
        install(WebSockets)
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("seina_chan_prefs")
        }
    }

    @Provides
    @Singleton
    fun provideHermesWsClient(client: HttpClient, json: Json, networkMonitor: NetworkMonitor): HermesWsClient {
        return HermesWsClient(client, json, networkMonitor)
    }

    @Provides
    @Singleton
    fun provideConnectionRepository(
        wsClient: HermesWsClient,
        dataStore: DataStore<Preferences>
    ): ConnectionRepository {
        return ConnectionRepository(wsClient, dataStore)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "seina_chan_db"
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    @Provides
    @Singleton
    fun provideSentImageDao(database: AppDatabase): SentImageDao {
        return database.sentImageDao()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun provideSessionRepository(
        wsClient: HermesWsClient,
        sentImageDao: SentImageDao
    ): SessionRepository {
        return SessionRepository(wsClient, sentImageDao)
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        @ApplicationContext context: Context,
        wsClient: HermesWsClient,
        sentImageDao: SentImageDao,
        messageDao: MessageDao
    ): ChatRepository {
        return ChatRepository(context, wsClient, sentImageDao, messageDao)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        dataStore: DataStore<Preferences>
    ): SettingsRepository {
        return SettingsRepository(dataStore)
    }
}
