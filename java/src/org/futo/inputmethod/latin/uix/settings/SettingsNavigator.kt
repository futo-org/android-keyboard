package org.futo.inputmethod.latin.uix.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.settings.pages.HomeScreen
import org.futo.inputmethod.latin.uix.settings.pages.PredictiveTextScreen
import org.futo.inputmethod.latin.uix.settings.pages.ThemeScreen
import org.futo.inputmethod.latin.uix.settings.pages.TypingScreen
import org.futo.inputmethod.latin.uix.settings.pages.VoiceInputScreen

@Composable
fun SettingsNavigator(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") { HomeScreen(navController) }
        composable("predictiveText") { PredictiveTextScreen(navController) }
        composable("typing") { TypingScreen(navController) }
        composable("voiceInput") { VoiceInputScreen(navController) }
        composable("themes") { ThemeScreen(navController) }
    }
}