package com.localai.chat.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.localai.chat.ui.chat.ChatRoute
import com.localai.chat.ui.navigation.AppRoute
import com.localai.chat.ui.settings.SettingsRoute
import com.localai.chat.ui.theme.LocalAiChatTheme

@Composable
fun LocalAiChatApp() {
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
    ) {
        NavHost(
            navController = navController,
            startDestination = AppRoute.Chat.route,
        ) {
            composable(AppRoute.Chat.route) {
                ChatRoute(
                    onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
                )
            }
            composable(AppRoute.Settings.route) {
                SettingsRoute(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LocalAiChatAppPreview() {
    LocalAiChatTheme {
        LocalAiChatApp()
    }
}
