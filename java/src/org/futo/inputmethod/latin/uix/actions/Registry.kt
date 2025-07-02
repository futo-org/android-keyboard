package org.futo.inputmethod.latin.uix.actions

import android.content.Context
import androidx.collection.mutableIntSetOf
import androidx.core.content.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import org.futo.inputmethod.keyboard.internal.KeyboardCodesSet
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.PreferenceUtils
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.actions.fonttyper.FontTyperAction
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSettingBlocking

// Note: indices must stay stable
val AllActionsMap = mapOf(
    "emoji" to EmojiAction,
    "settings" to SettingsAction,
    "paste" to PasteAction,
    "text_edit" to TextEditAction,
    "themes" to ThemeAction,
    "undo" to UndoAction,
    "redo" to RedoAction,
    "voice_input" to VoiceInputAction,
    "system_voice_input" to SystemVoiceInputAction,
    "switch_language" to SwitchLanguageAction,
    "clipboard_history" to ClipboardHistoryAction,
    "mem_dbg" to MemoryDebugAction,
    "cut" to CutAction,
    "copy" to CopyAction,
    "select_all" to SelectAllAction,
    "more" to MoreActionsAction,
    "bugs" to BugViewerAction,
    "keyboard_modes" to KeyboardModeAction,
    "up" to ArrowUpAction,
    "down" to ArrowDownAction,
    "left" to ArrowLeftAction,
    "right" to ArrowRightAction,
    "font_typer" to FontTyperAction
)

val ActionToId = AllActionsMap.entries.associate { it.value to it.key }

val AllActions = AllActionsMap.values.toList().verifyNamesAreUnique()
val AllActionKeys = AllActionsMap.keys.toList()

val ActionIdToInt = AllActionsMap.entries.associate { it.key to AllActions.indexOf(it.value) }

val Action.keyCode
    get() = AllActions.indexOf(this) + Constants.CODE_ACTION_0

val Action.keyCodeAlt
    get() = AllActions.indexOf(this) + Constants.CODE_ALT_ACTION_0

// Name integers of actions must be unique
private fun List<Action>.verifyNamesAreUnique(): List<Action> {
    val names = mutableIntSetOf()
    forEach {
        assert(!names.contains(it.name)) { "The action $it contains a duplicate name!" }
    }
    return this
}

object ActionRegistry {
    suspend fun getActionOverride(context: Context, action: Action): Action {
        return if(action == VoiceInputAction || action == SystemVoiceInputAction) {
            val useSystemVoiceInput = context.getSetting(USE_SYSTEM_VOICE_INPUT)
            if(useSystemVoiceInput) {
                SystemVoiceInputAction
            } else {
                VoiceInputAction
            }
        } else {
            action
        }
    }

    fun parseAction(actionString: String): Int {
        val action = if(actionString.startsWith(KeyboardCodesSet.ACTION_CODE_PREFIX)) {
            actionString.substring(KeyboardCodesSet.ACTION_CODE_PREFIX.length)
        } else {
            actionString
        }

        return action.toIntOrNull() ?: ActionIdToInt[action] ?: throw IllegalArgumentException("Unknown action $actionString")
    }

    fun actionIdToName(context: Context, id: Int): String {
        return context.getString(AllActions[id].name)
    }

    fun actionStringIdToIdx(id: String): Int {
        return AllActionsMap.keys.indexOf(id)
    }

    fun actionToStringId(action: Action): String {
        return AllActionsMap.entries.find { it.value == action }?.key ?: ""
    }
}


enum class ActionCategory {
    ActionKey,
    PinnedKey,
    Favorites,
    More,
    Disabled
}

fun ActionCategory.name(context: Context): String {
    return context.getString(when(this) {
        ActionCategory.ActionKey -> R.string.action_kind_action_key
        ActionCategory.PinnedKey -> R.string.action_kind_pinned_key
        ActionCategory.Favorites -> R.string.action_kind_favorites
        ActionCategory.More -> R.string.action_kind_more
        ActionCategory.Disabled -> R.string.action_kind_disabled
    })
}

fun ActionCategory.toSepName(): String {
    return "_SEP_${name}"
}

val ActionCategorySepNames = List(ActionCategory.entries.size) { i ->
    Pair(
        ActionCategory.entries[i].toSepName(),
        ActionCategory.entries[i],
    )
}.toMap()

sealed class ActionEditorItem {
    data class Item(val action: Action) : ActionEditorItem()
    data class Separator(val category: ActionCategory) : ActionEditorItem()
}

fun ActionEditorItem.toKey(): String {
    return when(this) {
        is ActionEditorItem.Item -> this.action.name.toString()
        is ActionEditorItem.Separator -> "sep " + this.category.name
    }
}

fun ActionEditorItem.stringRepresentation(): String = when(this) {
    is ActionEditorItem.Item -> AllActionsMap.entries.find { it.value == action }!!.key
    is ActionEditorItem.Separator -> "_SEP_" + this.category.name
}

fun List<ActionEditorItem>.serializeActionEditorItemListToString(): String = joinToString { it.stringRepresentation() }

fun String.toActionEditorItems() : List<ActionEditorItem> = split(",").mapNotNull {
    val v = it.trimStart()
    when {
        ActionCategorySepNames.containsKey(v) -> ActionEditorItem.Separator(ActionCategorySepNames[v]!!)
        AllActionsMap.containsKey(v) -> ActionEditorItem.Item(AllActionsMap[v]!!)
        else -> null
    }
}

fun List<ActionEditorItem>.toActionMap(): Map<ActionCategory, List<Action>> {
    val actionMap = mutableMapOf<ActionCategory, MutableList<Action>>()
    var currentCategory: ActionCategory? = null

    for (item in this) {
        when (item) {
            is ActionEditorItem.Item -> {
                currentCategory?.let { category ->
                    actionMap.getOrPut(category) { mutableListOf() }.add(item.action)
                }
            }
            is ActionEditorItem.Separator -> {
                currentCategory = item.category
                if (!actionMap.containsKey(currentCategory)) {
                    actionMap[currentCategory] = mutableListOf()
                }
            }
        }
    }

    return actionMap
}

// Initializes any non-present categories to be empty
fun Map<ActionCategory, List<Action>>.ensureAllCategoriesPresent(): Map<ActionCategory, List<Action>> {
    val map = toMutableMap()

    ActionCategory.entries.forEach {
        if(!map.containsKey(it)) {
            map[it] = listOf()
        }
    }

    return map
}

// Removes any duplicate actions
fun Map<ActionCategory, List<Action>>.removeDuplicateActions(): Map<ActionCategory, List<Action>> {
    val actions = mutableSetOf<Action>()

    return ActionCategory.entries.associate { category ->
        category to this[category]!!.filter { action ->
            (!actions.contains(action)).apply { actions.add(action) }
        }
    }
}

// Adds any missing actions to the "More" section
fun Map<ActionCategory, List<Action>>.ensureAllActionsPresent(): Map<ActionCategory, List<Action>> {
    val map = toMutableMap()

    val actionsPresent = mutableSetOf<Action>()
    values.forEach { v -> actionsPresent.addAll(v) }

    val actionsRequired = AllActions.toSet()

    val actionsMissing = actionsRequired.subtract(actionsPresent)

    if(actionsMissing.isNotEmpty()) {
        map[ActionCategory.More] = map[ActionCategory.More]!! + actionsMissing.filter { it.shownInEditor }
        map[ActionCategory.Disabled] = map[ActionCategory.Disabled]!! + actionsMissing.filter { !it.shownInEditor }
    }

    return map
}

// Flattens the map back to a list
fun Map<ActionCategory, List<Action>>.flattenToActionEditorItems(): List<ActionEditorItem> {
    val result = mutableListOf<ActionEditorItem>()

    for (category in ActionCategory.entries) {
        if (this.containsKey(category)) {
            result.add(ActionEditorItem.Separator(category))
            this[category]?.let { actions ->
                result.addAll(actions.map {
                    ActionEditorItem.Item(it)
                })
            }
        }
    }

    return result

}

fun List<ActionEditorItem>.ensureWellFormed(): List<ActionEditorItem> {
    var map = toActionMap()
    map = map.ensureAllCategoriesPresent()
    map = map.removeDuplicateActions()
    map = map.ensureAllActionsPresent()
    return map.flattenToActionEditorItems()
}

fun List<Action>.serializeActionListToString(): String = joinToString(separator = ",") { action ->
    AllActionsMap.entries.find { it.value == action }!!.key
}

fun String.toActionList(): List<Action> = split(",").mapNotNull { AllActionsMap[it.trim()] }

val DefaultActionSettings = mapOf(
    ActionCategory.ActionKey to listOf(EmojiAction),
    ActionCategory.PinnedKey to listOf(VoiceInputAction),
    ActionCategory.Favorites to listOf(SwitchLanguageAction, UndoAction, RedoAction, TextEditAction, ClipboardHistoryAction, ThemeAction, KeyboardModeAction),
    ActionCategory.More to listOf(), // Remaining actions get populated automatically by ensureWellFormed
    ActionCategory.Disabled to listOf(MemoryDebugAction, SystemVoiceInputAction, BugViewerAction)
)

val DefaultActionKey = DefaultActionSettings[ActionCategory.ActionKey]!!.firstOrNull()?.let {
    ActionRegistry.actionToStringId(it)
} ?: ""

val ActionsSettings = SettingsKey(
    stringPreferencesKey("actions_settings_map"),
    DefaultActionSettings.flattenToActionEditorItems().ensureWellFormed().serializeActionEditorItemListToString()
)

val PinnedActions = SettingsKey(
    stringPreferencesKey("pinned_actions_s"),
    DefaultActionSettings[ActionCategory.PinnedKey]!!.serializeActionListToString()
)

val FavoriteActions = SettingsKey(
    stringPreferencesKey("favorite_actions_s"),
    DefaultActionSettings[ActionCategory.Favorites]!!.serializeActionListToString()
)


fun Context.updateSettingsWithNewActions(newActions: Map<ActionCategory, List<Action>>) {
    val map = newActions.flattenToActionEditorItems().ensureWellFormed().toActionMap()

    val actionKey = map[ActionCategory.ActionKey]?.firstOrNull()

    val sharedPrefs = PreferenceUtils.getDefaultSharedPreferences(this)
    sharedPrefs.edit {
        putString(Settings.PREF_ACTION_KEY_ID, actionKey?.let {
            ActionRegistry.actionToStringId(it)
        } ?: "")
    }

    setSettingBlocking(ActionsSettings.key, map.flattenToActionEditorItems().serializeActionEditorItemListToString())

    setSettingBlocking(PinnedActions.key, (map[ActionCategory.PinnedKey] ?: listOf()).serializeActionListToString())
    setSettingBlocking(FavoriteActions.key, (map[ActionCategory.Favorites] ?: listOf()).serializeActionListToString())
}