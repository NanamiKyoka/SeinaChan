package com.seina.chan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.seina.chan.data.repository.SettingsRepository
import com.seina.chan.data.model.BUILTIN_THEMES
import com.seina.chan.data.model.ThemeConfig
import com.seina.chan.service.HermesConnectionService
import com.seina.chan.ui.components.SeinaSnackbarHost
import com.seina.chan.ui.navigation.SeinaNavHost
import com.seina.chan.ui.theme.SeinaChanTheme
import com.seina.chan.util.FileLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    private var connectionService: HermesConnectionService? = null
    private var serviceBound = false
    private var navController: NavHostController? = null
    private val _navigateToChatEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HermesConnectionService.LocalBinder
            connectionService = binder.getService()
            serviceBound = true
            FileLogger.i("MainActivity", "Service connected")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            connectionService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindHermesService()
        setupProcessLifecycleObserver()
        handleIntent(intent)
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val activeThemeId by settingsRepository.activeThemeId.collectAsStateWithLifecycle(initialValue = "warm-sun")
            val fontPresetId by settingsRepository.fontPresetId.collectAsStateWithLifecycle(initialValue = "serif-sans")
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val themeConfig = remember(activeThemeId) {
                BUILTIN_THEMES.find { it.id == activeThemeId }
            }
            SeinaChanTheme(
                darkTheme = darkTheme,
                themeConfig = themeConfig,
                fontPresetId = fontPresetId,
            ) {
                val snackbarHostState = remember { SnackbarHostState() }
                val navControllerLocal = rememberNavController()
                navController = navControllerLocal

                Scaffold(
                    snackbarHost = { SeinaSnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.navigationBars
                ) { innerPadding ->
                    SeinaNavHost(
                        navController = navControllerLocal,
                        snackbarHostState = snackbarHostState,
                        navigateToChatEvent = _navigateToChatEvent,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun bindHermesService() {
        val intent = Intent(this, HermesConnectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        // 通知 Service 应用回到前台，并触发连接检查/立即重连
        val intent = Intent(this, HermesConnectionService::class.java).apply {
            action = HermesConnectionService.ACTION_APP_FOREGROUND
        }
        startService(intent)

        val ensureIntent = Intent(this, HermesConnectionService::class.java).apply {
            action = HermesConnectionService.ACTION_ENSURE_CONNECTION
        }
        startService(ensureIntent)
    }

    override fun onPause() {
        super.onPause()
        // 通知 Service 应用进入后台
        val intent = Intent(this, HermesConnectionService::class.java).apply {
            action = HermesConnectionService.ACTION_APP_BACKGROUND
        }
        startService(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun setupProcessLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> AppForegroundTracker.setForeground(true)
                    Lifecycle.Event.ON_STOP -> AppForegroundTracker.setForeground(false)
                    else -> Unit
                }
            }
        )
    }

    private fun handleIntent(intent: Intent?) {
        val sessionId = intent?.getStringExtra("sessionId") ?: return
        val eventType = intent.getStringExtra("eventType")
        FileLogger.i("MainActivity", "handleIntent sessionId=$sessionId, eventType=$eventType")
        _navigateToChatEvent.tryEmit(sessionId)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
