package com.localai.chat.ui.navigation

sealed class AppRoute(val route: String) {
    data object Chat : AppRoute("chat")
    data object Settings : AppRoute("settings")
}
