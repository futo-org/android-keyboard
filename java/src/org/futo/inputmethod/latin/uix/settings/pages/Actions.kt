package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.actions.ActionCategory
import org.futo.inputmethod.latin.uix.actions.ActionsSettings
import org.futo.inputmethod.latin.uix.actions.AllActionsMap
import org.futo.inputmethod.latin.uix.actions.EmojiAction
import org.futo.inputmethod.latin.uix.actions.SwitchLanguageAction
import org.futo.inputmethod.latin.uix.actions.ensureAllCategoriesPresent
import org.futo.inputmethod.latin.uix.actions.ensureWellFormed
import org.futo.inputmethod.latin.uix.actions.toActionEditorItems
import org.futo.inputmethod.latin.uix.actions.toActionMap
import org.futo.inputmethod.latin.uix.actions.updateSettingsWithNewActions
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.urlDecode
import org.futo.inputmethod.latin.uix.urlEncode

@Preview(showBackground = true)
@Composable
fun ActionsScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current

    val actionMap = useDataStoreValue(ActionsSettings)
        .toActionEditorItems()
        .ensureWellFormed()
        .toActionMap()

    val isLanguageKeyEnabled = actionMap[ActionCategory.ActionKey]?.firstOrNull() == SwitchLanguageAction


    ScrollableList {
        ScreenTitle(stringResource(R.string.action_settings_title), showBack = true, navController)

        ScreenTitle(stringResource(R.string.action_settings_quick_options_title))
        SettingToggleRaw(
            title = stringResource(R.string.action_settings_quick_option_language_switch_key),
            subtitle = stringResource(R.string.action_settings_quick_option_language_switch_key_subtitle),
            enabled = isLanguageKeyEnabled,
            setValue = { to ->
                var actionMap = context.getSetting(ActionsSettings)
                    .toActionEditorItems()
                    .ensureWellFormed()
                    .toActionMap()
                    .ensureAllCategoriesPresent()
                    .toMutableMap()

                if(to) {
                    // Add old action key to the favorites instead, if there is any.
                    val prevActionKey = actionMap[ActionCategory.ActionKey]!!
                    actionMap[ActionCategory.Favorites] =
                        prevActionKey + actionMap[ActionCategory.Favorites]!!

                    // Replace it with switch language action
                    actionMap[ActionCategory.ActionKey] = listOf(SwitchLanguageAction)
                } else {
                    // Remove language switch key and emoji key completely
                    actionMap = actionMap.mapValues { it.value.filter { it != EmojiAction && it != SwitchLanguageAction } }.toMutableMap()

                    // Reinsert switch language to beginning of favorites
                    actionMap[ActionCategory.Favorites] = listOf(SwitchLanguageAction) + actionMap[ActionCategory.Favorites]!!

                    // Reinsert emoji to action key
                    actionMap[ActionCategory.ActionKey] = listOf(EmojiAction)
                }

                context.updateSettingsWithNewActions(actionMap)
            }
        )

        ScreenTitle(stringResource(R.string.action_settings_action_settings))
        AllActionsMap.forEach {
            val action = it.value
            if(action.settingsMenu != null) {
                NavigationItem(
                    title = stringResource(action.name),
                    style = NavigationItemStyle.HomeTertiary,
                    navigate = {
                        navController.navigate("actions/" + it.key.urlEncode())
                    },
                    icon = painterResource(action.icon)
                )
            }
        }
    }
}

@Composable
@Preview
fun ActionsScreenForAction(actionName: String = "clipboard_history", navController: NavHostController = rememberNavController()) {
    val action = remember(actionName) {
        AllActionsMap[actionName]
    } ?: return

    val actionSettings = action.settingsMenu ?: return

    ScrollableList {
        ScreenTitle(stringResource(actionSettings.title), showBack = true, navController)

        actionSettings.settings.forEach {
            it.component()
        }
    }
}

fun NavGraphBuilder.addActionsNavigation(
    navController: NavHostController
) {
    composable("actions") { ActionsScreen(navController) }
    composable("actions/{action}") {
        ActionsScreenForAction(it.arguments?.getString("action")?.urlDecode() ?: return@composable, navController)
    }
}