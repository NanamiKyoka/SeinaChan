package com.seina.chan.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.seina.chan.ui.components.GlobalEventHandler
import com.seina.chan.ui.screens.chat.ChatScreen
import com.seina.chan.ui.screens.connect.ConnectScreen
import com.seina.chan.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableSharedFlow

object Routes {
    const val CONNECT = "connect"
    const val CHAT = "chat/{sessionId}"
    const val SETTINGS = "settings"

    fun chat(sessionId: String = "") = "chat/$sessionId"
}

@Composable
fun SeinaNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    navigateToChatEvent: MutableSharedFlow<String>? = null,
    startDestination: String = Routes.CONNECT,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        navigateToChatEvent?.collect { sessionId ->
            navController.navigate(Routes.chat(sessionId))
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.CONNECT) {
            ConnectScreen(
                onConnected = { navController.navigate(Routes.chat()) }
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("sessionId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            ChatScreen(
                viewModel = hiltViewModel(),
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onReconfigure = {
                    navController.navigate(Routes.CONNECT) {
                        popUpTo(Routes.CONNECT) { inclusive = true }
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                navController = navController
            )
        }
    }

    GlobalEventHandler(snackbarHostState = snackbarHostState)
}
