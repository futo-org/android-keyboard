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
import org.futo.inputmethod.latin.uix.settings.pages.AdvancedParametersScreen
import org.futo.inputmethod.latin.uix.settings.pages.AlreadyPaidDialog
import org.futo.inputmethod.latin.uix.settings.pages.BlacklistScreen
import org.futo.inputmethod.latin.uix.settings.pages.CreditsScreen
import org.futo.inputmethod.latin.uix.settings.pages.DevEditTextVariationsScreen
import org.futo.inputmethod.latin.uix.settings.pages.DevKeyboardScreen
import org.futo.inputmethod.latin.uix.settings.pages.DevLayoutEdit
import org.futo.inputmethod.latin.uix.settings.pages.DevLayoutEditor
import org.futo.inputmethod.latin.uix.settings.pages.DevLayoutList
import org.futo.inputmethod.latin.uix.settings.pages.DeveloperScreen
import org.futo.inputmethod.latin.uix.settings.pages.HelpScreen
import org.futo.inputmethod.latin.uix.settings.pages.HomeScreen
import org.futo.inputmethod.latin.uix.settings.pages.LanguagesScreen
import org.futo.inputmethod.latin.uix.settings.pages.PaymentScreen
import org.futo.inputmethod.latin.uix.settings.pages.PaymentThankYouScreen
import org.futo.inputmethod.latin.uix.settings.pages.PredictiveTextScreen
import org.futo.inputmethod.latin.uix.settings.pages.ProjectInfoView
import org.futo.inputmethod.latin.uix.settings.pages.SelectLanguageScreen
import org.futo.inputmethod.latin.uix.settings.pages.SelectLayoutsScreen
import org.futo.inputmethod.latin.uix.settings.pages.ThemeScreen
import org.futo.inputmethod.latin.uix.settings.pages.VoiceInputScreen
import org.futo.inputmethod.latin.uix.settings.pages.addActionsNavigation
import org.futo.inputmethod.latin.uix.settings.pages.addModelManagerNavigation
import org.futo.inputmethod.latin.uix.settings.pages.addTypingNavigation
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
        composable("addLanguage") { SelectLanguageScreen(navController) }
        composable("addLayout/{lang}") { SelectLayoutsScreen(navController, it.arguments?.getString("lang")?.urlDecode() ?: "") }
        composable("predictiveText") { PredictiveTextScreen(navController) }
        composable("advancedparams") { AdvancedParametersScreen(navController) }
        addTypingNavigation(navController)
        composable("voiceInput") { VoiceInputScreen(navController) }
        composable("themes") { ThemeScreen(navController) }
        composable("help") { HelpScreen(navController) }
        composable("developer") { DeveloperScreen(navController) }
        composable("devtextedit") { DevEditTextVariationsScreen(navController) }
        composable("devlayouts") { DevLayoutList(navController) }
        composable("devlayouteditor") { DevLayoutEditor(navController) }
        composable("devkeyboard") { DevKeyboardScreen(navController) }
        composable("devlayoutedit/{i}") { DevLayoutEdit(navController, it.arguments!!.getString("i")!!.toInt()) }
        composable("blacklist") { BlacklistScreen(navController) }
        composable("payment") { PaymentScreen(navController) { navController.navigateUp() } }
        composable("paid") { PaymentThankYouScreen { navController.navigateUp() } }
        composable("credits") { CreditsScreen(navController) }
        composable("credits/thirdparty/{idx}") {
            ProjectInfoView(
                it.arguments?.getString("idx")?.toIntOrNull() ?: 0,
                navController
            )
        }
        dialog("error/{title}/{body}") {
            ErrorDialog(
                it.arguments?.getString("title")?.urlDecode() ?: stringResource(R.string.settings_unknown_error_title),
                it.arguments?.getString("body")?.urlDecode() ?: stringResource(R.string.settings_unknown_error_subtitle),
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
        dialog("alreadyPaid") {
            AlreadyPaidDialog(navController = navController)
        }
        addModelManagerNavigation(navController)
        addActionsNavigation(navController)
    }
}