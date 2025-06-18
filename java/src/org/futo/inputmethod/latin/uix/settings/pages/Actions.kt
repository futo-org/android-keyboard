package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.actions.ActionCategory
import org.futo.inputmethod.latin.uix.actions.ActionsEditor
import org.futo.inputmethod.latin.uix.actions.ActionsSettings
import org.futo.inputmethod.latin.uix.actions.AllActionsMap
import org.futo.inputmethod.latin.uix.actions.EmojiAction
import org.futo.inputmethod.latin.uix.actions.SwitchLanguageAction
import org.futo.inputmethod.latin.uix.actions.ensureWellFormed
import org.futo.inputmethod.latin.uix.actions.toActionEditorItems
import org.futo.inputmethod.latin.uix.actions.toActionMap
import org.futo.inputmethod.latin.uix.actions.updateSettingsWithNewActions
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem
import org.futo.inputmethod.latin.uix.urlEncode

val ActionsScreen = UserSettingsMenu(
    title = R.string.action_settings_title,
    navPath = "actions", registerNavPath = true,
    settings = listOf(
        userSettingDecorationOnly { ScreenTitle(stringResource(R.string.action_settings_quick_options_title)) },

        UserSetting(
            name = R.string.action_settings_quick_option_enable_action_key,
            subtitle = R.string.action_settings_quick_option_enable_action_key_subtitle_no_assignment
        ) {
            val context = LocalContext.current
            val actionMap = useDataStoreValue(ActionsSettings)
                .toActionEditorItems()
                .ensureWellFormed()
                .toActionMap()

            val currActionKey = actionMap[ActionCategory.ActionKey]?.firstOrNull()

            SettingToggleRaw(
                title = stringResource(R.string.action_settings_quick_option_enable_action_key),
                subtitle = when(currActionKey) {
                    null -> stringResource(R.string.action_settings_quick_option_enable_action_key_subtitle_no_assignment)
                    else -> stringResource(
                        R.string.action_settings_quick_option_enable_action_key_subtitle_with_assignment,
                        stringResource(currActionKey.name)
                    )
                },
                enabled = currActionKey != null,
                setValue = { to ->
                    var actionMap = context.getSetting(ActionsSettings)
                        .toActionEditorItems()
                        .ensureWellFormed()
                        .toActionMap()
                        .toMutableMap()

                    if(to) {
                        // Put an emoji key as the default.
                        actionMap[ActionCategory.ActionKey] = listOf(EmojiAction)

                        // In case it was assigned anywhere else, it will be removed automatically
                        // when we're removing duplicates. The ActionKey has the highest precedence
                    } else {
                        // Move any previous action key to the favorites
                        val prevActionKey = actionMap[ActionCategory.ActionKey]!!
                        actionMap[ActionCategory.Favorites] =
                            prevActionKey + actionMap[ActionCategory.Favorites]!!

                        // Then clear action key
                        actionMap[ActionCategory.ActionKey] = listOf()
                    }

                    context.updateSettingsWithNewActions(actionMap)
                }
            )
        },

        UserSetting(
            name = R.string.action_settings_quick_option_language_switch_key,
            subtitle = R.string.action_settings_quick_option_language_switch_key_subtitle,
            visibilityCheck = {
                // Do not show the language key setting if action key is disabled entirely.
                val actionMap = useDataStoreValue(ActionsSettings)
                    .toActionEditorItems()
                    .ensureWellFormed()
                    .toActionMap()

                val currActionKey = actionMap[ActionCategory.ActionKey]?.firstOrNull()

                currActionKey != null
            }
        ) {
            val context = LocalContext.current
            val actionMap = useDataStoreValue(ActionsSettings)
                .toActionEditorItems()
                .ensureWellFormed()
                .toActionMap()

            val currActionKey = actionMap[ActionCategory.ActionKey]?.firstOrNull()
            val isLanguageKeyEnabled = currActionKey == SwitchLanguageAction

            SettingToggleRaw(
                title = stringResource(R.string.action_settings_quick_option_language_switch_key),
                subtitle = stringResource(R.string.action_settings_quick_option_language_switch_key_subtitle),
                enabled = isLanguageKeyEnabled,
                disabled = currActionKey == null,
                setValue = { to ->
                    var actionMap = context.getSetting(ActionsSettings)
                        .toActionEditorItems()
                        .ensureWellFormed()
                        .toActionMap()
                        .toMutableMap()

                    if (to) {
                        // Add old action key to the favorites instead, if there is any.
                        val prevActionKey = actionMap[ActionCategory.ActionKey]!!
                        actionMap[ActionCategory.Favorites] =
                            prevActionKey + actionMap[ActionCategory.Favorites]!!.filter { it != SwitchLanguageAction }

                        // Replace it with switch language action
                        actionMap[ActionCategory.ActionKey] = listOf(SwitchLanguageAction)
                    } else {
                        // Remove language switch key and emoji key completely
                        actionMap =
                            actionMap.mapValues { it.value.filter { it != EmojiAction && it != SwitchLanguageAction } }
                                .toMutableMap()

                        // Reinsert switch language to beginning of favorites
                        actionMap[ActionCategory.Favorites] =
                            listOf(SwitchLanguageAction) + actionMap[ActionCategory.Favorites]!!

                        // Reinsert emoji to action key
                        actionMap[ActionCategory.ActionKey] = listOf(EmojiAction)
                    }

                    context.updateSettingsWithNewActions(actionMap)
                }
            )
        },

        // TODO: Add a "Show voice input button" toggle

        userSettingNavigationItem(
            title = R.string.action_editor_title,
            subtitle = R.string.action_editor_subtitle,
            style = NavigationItemStyle.Misc,
            navigateTo = "actionEdit"
        ),

        userSettingDecorationOnly {
            ScreenTitle(stringResource(R.string.action_settings_action_settings))
        }
    ) + AllActionsMap.mapNotNull { v ->
        v.value.settingsMenu?.let {
            userSettingNavigationItem(
                title = v.value.name,
                style = NavigationItemStyle.HomeTertiary,
                navigateTo = "actions/" + v.key.urlEncode(),
                icon = v.value.icon
            )
        }
    }
)

@Preview(showBackground = true)
@Composable
fun ActionEditorScreen(navController: NavHostController = rememberNavController()) {
    Column {
        ScreenTitle(stringResource(R.string.action_editor_title), showBack = true, navController)
        ActionsEditor { }
    }
}