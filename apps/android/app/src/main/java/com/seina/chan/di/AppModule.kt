package com.seina.chan.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.seina.chan.data.local.AppDatabase
import com.seina.chan.data.local.dao.MessageDao
import com.seina.chan.data.local.dao.SentImageDao
import com.seina.chan.data.local.dao.SessionDao
import com.seina.chan.data.remote.HermesWsClient
import com.seina.chan.data.repository.ChatRepository
import com.seina.chan.data.repository.ConnectionRepository
import com.seina.chan.data.repository.SessionRepository
import com.seina.chan.data.repository.SettingsRepository
import com.seina.chan.data.repository.AuthRepository
import com.seina.chan.util.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.serialization.json.Json
import javax.inject.Singleton
import javax.inject.Named

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
    @Named("ws")
    fun provideHttpClient(): HttpClient = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 15_000  // 每 15s 发送 ping，保活 socket
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 300_000  // 5 分钟，防止后台短暂挂起导致 socket 超时
        }
    }
    @Provides
    @Singleton
    @Named("api")
    fun provideApiHttpClient(): HttpClient = HttpClient(CIO) {
        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 30_000
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
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(context).maxSizePercent(0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideHermesWsClient(@Named("ws") client: HttpClient, json: Json, networkMonitor: NetworkMonitor): HermesWsClient {
        return HermesWsClient(client, json, networkMonitor)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(@Named("api") apiHttpClient: HttpClient, json: Json): AuthRepository {
        return AuthRepository(apiHttpClient, json)
    }

    @Provides
    @Singleton
    fun provideConnectionRepository(
        wsClient: HermesWsClient,
        dataStore: DataStore<Preferences>,
        authRepository: AuthRepository,
        chatRepository: ChatRepository
    ): ConnectionRepository {
        return ConnectionRepository(wsClient, dataStore, authRepository, chatRepository)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "seina_chan_db"
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
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
    fun provideSessionDao(database: AppDatabase): SessionDao {
        return database.sessionDao()
    }


    @Provides
    @Singleton
    fun provideSessionRepository(
        wsClient: HermesWsClient,
        sentImageDao: SentImageDao,
        sessionDao: SessionDao
    ): SessionRepository {
        return SessionRepository(wsClient, sentImageDao, sessionDao)
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
