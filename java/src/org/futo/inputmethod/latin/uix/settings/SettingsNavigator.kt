package org.futo.inputmethod.latin.uix.settings

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import org.futo.inputmethod.engine.IMESettingsMenu
import org.futo.inputmethod.engine.SettingsByLanguage
import org.futo.inputmethod.latin.toLocale
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
import org.futo.inputmethod.latin.uix.settings.pages.DevThemeImportScreen
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
import org.futo.inputmethod.latin.uix.settings.pages.NumberRowSettingMenu
import org.futo.inputmethod.latin.uix.settings.pages.PaymentScreen
import org.futo.inputmethod.latin.uix.settings.pages.PaymentThankYouScreen
import org.futo.inputmethod.latin.uix.settings.pages.PredictiveTextMenu
import org.futo.inputmethod.latin.uix.settings.pages.ProjectInfoView
import org.futo.inputmethod.latin.uix.settings.pages.ResizeMenuLite
import org.futo.inputmethod.latin.uix.settings.pages.ResizeScreen
import org.futo.inputmethod.latin.uix.settings.pages.SearchScreen
import org.futo.inputmethod.latin.uix.settings.pages.SelectLanguageScreen
import org.futo.inputmethod.latin.uix.settings.pages.SelectLayoutsScreen
import org.futo.inputmethod.latin.uix.settings.pages.TypingSettingsMenu
import org.futo.inputmethod.latin.uix.settings.pages.VoiceInputMenu
import org.futo.inputmethod.latin.uix.settings.pages.addModelManagerNavigation
import org.futo.inputmethod.latin.uix.settings.pages.buggyeditors.BuggyTextEditVariations
import org.futo.inputmethod.latin.uix.settings.pages.pdict.ConfirmDeleteExtraDictFileDialog
import org.futo.inputmethod.latin.uix.settings.pages.pdict.PersonalDictionaryLanguageList
import org.futo.inputmethod.latin.uix.settings.pages.pdict.PersonalDictionaryLanguageListForLocale
import org.futo.inputmethod.latin.uix.settings.pages.pdict.WordPopupDialogF
import org.futo.inputmethod.latin.uix.settings.pages.themes.CustomThemeScreen
import org.futo.inputmethod.latin.uix.settings.pages.themes.DeleteCustomThemeDialog
import org.futo.inputmethod.latin.uix.settings.pages.themes.ThemeScreen

// Utility function for quick error messages
fun NavHostController.navigateToError(title: String, body: String) {
    this.navigate(Route.Error(title, body))
}

fun NavHostController.navigateToInfo(title: String, body: String) {
    this.navigate(Route.Info(title, body))
}


object Route {
    @Serializable data class Error(val title: String, val body: String)
    @Serializable data class Info(val title: String, val body: String)
    @Serializable data class AddLayout(val lang: String)
    @Serializable data class PersonalDictList(val lang: String?)
    @Serializable data class PersonalDictWord(val lang: String?, val word: String?)
    @Serializable data class PersonalDictDelete(val dict: String)
    @Serializable data class DevLayoutEdit(val i: Int)
    @Serializable data class CustomTheme(val uri: String)
    @Serializable data class DeleteTheme(val name: String)
    @Serializable data class ThirdPartyInfo(val idx: Int)
}


val SettingsMenus = listOf(
    HomeScreenLite,
    LanguageSettingsLite,
    KeyboardSettingsMenu,
    NumberRowSettingMenu,
    TypingSettingsMenu,
    ResizeMenuLite,
    LongPressMenu,
    PredictiveTextMenu,
    BlacklistScreenLite,
    VoiceInputMenu,
    ActionsScreen,
    HelpMenu,
    MiscMenu,
    CreditsScreenLite,
    IMESettingsMenu
) + AllActions.mapNotNull { it.settingsMenu } + SettingsByLanguage.values


// Improves the semantics so that we don't have to deal with NavBackStackEntry when we don't need it
@JvmInline
value class NavGraphBuilderWrapper(val parent: NavGraphBuilder)
internal inline fun <reified T : Any> NavGraphBuilderWrapper.dialog(noinline content: @Composable (T) -> Unit) =
    parent.dialog<T> { content(it.toRoute()) }
internal inline fun <reified T : Any> NavGraphBuilderWrapper.composable(noinline content: @Composable (T) -> Unit) =
    parent.composable<T> { content(it.toRoute()) }


@Composable
fun SettingsNavigator(
    navController: NavHostController = rememberNavController()
) {
    val nav = navController
    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController = navController,
            startDestination = "home",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            with(NavGraphBuilderWrapper(this)) {
                composable<Route.AddLayout> { SelectLayoutsScreen(nav, it.lang.toLocale()) }

                parent.composable<Route.PersonalDictList> {
                    val route = it.toRoute<Route.PersonalDictList>()
                    PersonalDictionaryLanguageListForLocale(nav, it, route.lang?.toLocale())
                }
                dialog<Route.PersonalDictWord> { WordPopupDialogF(it.word, it.lang?.toLocale()) }
                dialog<Route.PersonalDictDelete> { ConfirmDeleteExtraDictFileDialog(it.dict) }

                composable<Route.DevLayoutEdit> { DevLayoutEdit(nav, it.i) }

                composable<Route.CustomTheme> { CustomThemeScreen(it.uri, nav) }
                dialog<Route.DeleteTheme> { DeleteCustomThemeDialog(it.name, nav) }

                composable<Route.ThirdPartyInfo> { ProjectInfoView(it.idx, nav) }

                dialog<Route.Error> { ErrorDialog(it.title, it.body, nav) }
                dialog<Route.Info> { InfoDialog(it.title, it.body, nav) }
            }
            composable("home") { HomeScreen(navController) }
            composable("search") { SearchScreen(navController) }
            composable("languages") { LanguagesScreen(navController) }
            composable("addLanguage") { SelectLanguageScreen(navController) }
            composable("pdict") {
                PersonalDictionaryLanguageList()
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
            composable("devbuggytextedit") { BuggyTextEditVariations(navController) }
            composable("devlayouts") { DevLayoutList(navController) }
            composable("devlayouteditor") { DevLayoutEditor(navController) }
            composable("devtheme") { DevThemeImportScreen(navController) }
            composable("devkeyboard") { DevKeyboardScreen(navController) }
            composable("blacklist") { BlacklistScreen(navController) }
            composable("payment") { PaymentScreen(navController) { navController.navigateUp() } }
            composable("paid") { PaymentThankYouScreen { navController.navigateUp() } }
            composable("credits") { CreditsScreen(navController) }
            composable("exportingcfg") { ExportingMenu(navController) }
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