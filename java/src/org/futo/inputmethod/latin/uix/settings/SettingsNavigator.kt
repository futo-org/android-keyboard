package org.futo.inputmethod.latin.uix.settings

import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.ErrorDialog
import org.futo.inputmethod.latin.uix.InfoDialog
import org.futo.inputmethod.latin.uix.settings.pages.AddLanguageScreen
import org.futo.inputmethod.latin.uix.settings.pages.AdvancedParametersScreen
import org.futo.inputmethod.latin.uix.settings.pages.BlacklistScreen
import org.futo.inputmethod.latin.uix.settings.pages.CreditsScreen
import org.futo.inputmethod.latin.uix.settings.pages.DevEditTextVariationsScreen
import org.futo.inputmethod.latin.uix.settings.pages.DeveloperScreen
import org.futo.inputmethod.latin.uix.settings.pages.HelpScreen
import org.futo.inputmethod.latin.uix.settings.pages.HomeScreen
import org.futo.inputmethod.latin.uix.settings.pages.LanguagesScreen
import org.futo.inputmethod.latin.uix.settings.pages.PaymentScreen
import org.futo.inputmethod.latin.uix.settings.pages.PaymentThankYouScreen
import org.futo.inputmethod.latin.uix.settings.pages.PredictiveTextScreen
import org.futo.inputmethod.latin.uix.settings.pages.ThemeScreen
import org.futo.inputmethod.latin.uix.settings.pages.TypingScreen
import org.futo.inputmethod.latin.uix.settings.pages.VoiceInputScreen
import org.futo.inputmethod.latin.uix.settings.pages.addModelManagerNavigation
import org.futo.inputmethod.latin.uix.urlDecode
import org.futo.inputmethod.latin.uix.urlEncode

// Utility function for quick error messages
fun NavHostController.navigateToError(title: String, body: String) {
    this.navigate("error/${title.urlEncode()}/${body.urlEncode()}")
}

fun NavHostController.navigateToInfo(title: String, body: String) {
    this.navigate("info/${title.urlEncode()}/${body.urlEncode()}")
}

@Composable
fun SettingsNavigator(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        exitTransition = { ExitTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable("home") { HomeScreen(navController) }
        composable("languages") { LanguagesScreen(navController) }
        composable("addLanguage") { AddLanguageScreen(navController) }
        composable("predictiveText") { PredictiveTextScreen(navController) }
        composable("advancedparams") { AdvancedParametersScreen(navController) }
        composable("typing") { TypingScreen(navController) }
        composable("voiceInput") { VoiceInputScreen(navController) }
        composable("themes") { ThemeScreen(navController) }
        composable("help") { HelpScreen(navController) }
        composable("developer") { DeveloperScreen(navController) }
        composable("devtextedit") { DevEditTextVariationsScreen(navController) }
        composable("blacklist") { BlacklistScreen(navController) }
        composable("payment") { PaymentScreen(navController) { navController.navigateUp() } }
        composable("paid") { PaymentThankYouScreen { navController.navigateUp() } }
        composable("credits") { CreditsScreen(navController) }
        dialog("error/{title}/{body}") {
            ErrorDialog(
                it.arguments?.getString("title")?.urlDecode() ?: stringResource(R.string.unknown_error),
                it.arguments?.getString("body")?.urlDecode() ?: stringResource(R.string.an_unknown_error_has_occurred),
                navController
            )
        }
        dialog("info/{title}/{body}") {
            InfoDialog(
                it.arguments?.getString("title")?.urlDecode() ?: "",
                it.arguments?.getString("body")?.urlDecode() ?: "",
                navController
            )
        }
        dialog("update") {
            UpdateDialog(navController = navController)
        }
        addModelManagerNavigation(navController)
    }
}