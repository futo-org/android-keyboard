package org.futo.inputmethod.latin.uix.settings

import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.ErrorDialog
import org.futo.inputmethod.latin.uix.InfoDialog
import org.futo.inputmethod.latin.uix.LocalNavController
import org.futo.inputmethod.latin.uix.SettingsExporter.ExportingMenu
import org.futo.inputmethod.latin.uix.actions.AllActions
import org.futo.inputmethod.latin.uix.settings.pages.ActionEditorScreen
import org.futo.inputmethod.latin.uix.settings.pages.ActionsScreen
import org.futo.inputmethod.latin.uix.settings.pages.AdvancedParametersScreen
import org.futo.inputmethod.latin.uix.settings.pages.AlreadyPaidDialog
import org.futo.inputmethod.latin.uix.settings.pages.BlacklistScreen
import org.futo.inputmethod.latin.uix.settings.pages.BlacklistScreenLite
import org.futo.inputmethod.latin.uix.settings.pages.CreditsScreen
import org.futo.inputmethod.latin.uix.settings.pages.CreditsScreenLite
import org.futo.inputmethod.latin.uix.settings.pages.DevEditTextVariationsScreen
import org.futo.inputmethod.latin.uix.settings.pages.DevKeyboardScreen
import org.futo.inputmethod.latin.uix.settings.pages.DevLayoutEdit
import org.futo.inputmethod.latin.uix.settings.pages.DevLayoutEditor
import org.futo.inputmethod.latin.uix.settings.pages.DevLayoutList
import org.futo.inputmethod.latin.uix.settings.pages.DeveloperScreen
import org.futo.inputmethod.latin.uix.settings.pages.HelpMenu
import org.futo.inputmethod.latin.uix.settings.pages.HomeScreen
import org.futo.inputmethod.latin.uix.settings.pages.HomeScreenLite
import org.futo.inputmethod.latin.uix.settings.pages.KeyboardAndTypingScreen
import org.futo.inputmethod.latin.uix.settings.pages.KeyboardSettingsMenu
import org.futo.inputmethod.latin.uix.settings.pages.LanguageSettingsLite
import org.futo.inputmethod.latin.uix.settings.pages.LanguagesScreen
import org.futo.inputmethod.latin.uix.settings.pages.LongPressMenu
import org.futo.inputmethod.latin.uix.settings.pages.MiscMenu
import org.futo.inputmethod.latin.uix.settings.pages.PaymentScreen
import org.futo.inputmethod.latin.uix.settings.pages.PaymentThankYouScreen
import org.futo.inputmethod.latin.uix.settings.pages.PredictiveTextMenu
import org.futo.inputmethod.latin.uix.settings.pages.ProjectInfoView
import org.futo.inputmethod.latin.uix.settings.pages.ResizeMenuLite
import org.futo.inputmethod.latin.uix.settings.pages.ResizeScreen
import org.futo.inputmethod.latin.uix.settings.pages.SearchScreen
import org.futo.inputmethod.latin.uix.settings.pages.SelectLanguageScreen
import org.futo.inputmethod.latin.uix.settings.pages.SelectLayoutsScreen
import org.futo.inputmethod.latin.uix.settings.pages.ThemeScreen
import org.futo.inputmethod.latin.uix.settings.pages.TypingSettingsMenu
import org.futo.inputmethod.latin.uix.settings.pages.VoiceInputMenu
import org.futo.inputmethod.latin.uix.settings.pages.GroqConfigScreen
import org.futo.inputmethod.latin.uix.settings.pages.AiReplyMenu
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

val SettingsMenus = listOf(
    HomeScreenLite,
    LanguageSettingsLite,
    KeyboardSettingsMenu,
    TypingSettingsMenu,
    ResizeMenuLite,
    LongPressMenu,
    PredictiveTextMenu,
    BlacklistScreenLite,
    VoiceInputMenu,
    AiReplyMenu,
    ActionsScreen,
    HelpMenu,
    MiscMenu,
    CreditsScreenLite
) + AllActions.mapNotNull { it.settingsMenu }

@Composable
fun SettingsNavigator(
    navController: NavHostController = rememberNavController()
) {
    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController = navController,
            startDestination = "home",
            exitTransition = { ExitTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable("home") { HomeScreen(navController) }
            composable("search") { SearchScreen(navController) }
            composable("languages") { LanguagesScreen(navController) }
            composable("addLanguage") { SelectLanguageScreen(navController) }
            composable("addLayout/{lang}") {
                SelectLayoutsScreen(
                    navController,
                    it.arguments?.getString("lang")?.urlDecode() ?: ""
                )
            }
            composable("advancedparams") { AdvancedParametersScreen(navController) }
            composable("actionEdit") { ActionEditorScreen(navController) }
            SettingsMenus.forEach { menu ->
                if(menu.registerNavPath) composable(menu.navPath) { UserSettingsMenuScreen(menu) }
            }
            composable("keyboardAndTyping") { KeyboardAndTypingScreen(navController) }
            composable("resize") { ResizeScreen(navController) }
            composable("themes") { ThemeScreen(navController) }
            composable("developer") { DeveloperScreen(navController) }
            composable("devtextedit") { DevEditTextVariationsScreen(navController) }
            composable("devlayouts") { DevLayoutList(navController) }
            composable("devlayouteditor") { DevLayoutEditor(navController) }
            composable("devkeyboard") { DevKeyboardScreen(navController) }
            composable("devlayoutedit/{i}") {
                DevLayoutEdit(
                    navController,
                    it.arguments!!.getString("i")!!.toInt()
                )
            }
            composable("blacklist") { BlacklistScreen(navController) }
            composable("payment") { PaymentScreen(navController) { navController.navigateUp() } }
            composable("paid") { PaymentThankYouScreen { navController.navigateUp() } }
            composable("credits") { CreditsScreen(navController) }
            composable("exportingcfg") { ExportingMenu(navController) }
            composable("groq") { GroqConfigScreen(navController) }
            composable("credits/thirdparty/{idx}") {
                ProjectInfoView(
                    it.arguments?.getString("idx")?.toIntOrNull() ?: 0,
                    navController
                )
            }
            dialog("error/{title}/{body}") {
                ErrorDialog(
                    it.arguments?.getString("title")?.urlDecode()
                        ?: stringResource(R.string.settings_unknown_error_title),
                    it.arguments?.getString("body")?.urlDecode()
                        ?: stringResource(R.string.settings_unknown_error_subtitle),
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
        }
    }
}