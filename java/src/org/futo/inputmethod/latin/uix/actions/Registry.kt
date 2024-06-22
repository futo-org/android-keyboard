package org.futo.inputmethod.latin.uix.actions

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import org.futo.inputmethod.keyboard.internal.KeyboardCodesSet
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.getSetting

val ExpandableActionItems = stringPreferencesKey("expandableActions")
val PinnedActionItems = stringPreferencesKey("pinnedActions")

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
    "select_all" to SelectAllAction
)

val ActionToId = AllActionsMap.entries.associate { it.value to it.key }

val AllActions = AllActionsMap.values.toList()

val ActionIdToInt = AllActionsMap.entries.associate { it.key to AllActions.indexOf(it.value) }

object ActionRegistry {
    val EnterActions = "!fixedColumnOrder!4,!needsDividers!," +
        listOf(SwitchLanguageAction, TextEditAction, ClipboardHistoryAction, EmojiAction, UndoAction, RedoAction).map {
            ActionToId[it]
        }.joinToString(separator = ",") {
            "!icon/action_$it|!code/action_$it"
        }

    fun stringToActions(string: String, defaults: List<Action>): List<Action> {
        return string.split(",").mapNotNull { idx ->
            idx.toIntOrNull()?.let { AllActions.getOrNull(it) }
        }.let { list ->
            val notIncluded = defaults.filter { action ->
                !list.contains(action)
            }

            list + notIncluded
        }
    }

    fun actionsToString(actions: List<Action>): String {
        return actions.map { AllActions.indexOf(it) }.joinToString(separator = ",")
    }

    fun moveElement(string: String, defaults: List<Action>, action: Action, direction: Int): String {
        val actions = stringToActions(string, defaults)
        val index = actions.indexOf(action)
        val filtered = actions.filter { it != action }.toMutableList()
        filtered.add((index + direction).coerceIn(0 .. filtered.size), action)
        return actionsToString(filtered)
    }

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
}

val DefaultActions = listOf(
    EmojiAction,
    TextEditAction,
    UndoAction,
    RedoAction,
    PasteAction,
    SettingsAction,
    ThemeAction,
    MemoryDebugAction,
    SwitchLanguageAction,
    ClipboardHistoryAction
)

val DefaultActionsString = ActionRegistry.actionsToString(DefaultActions)


val DefaultPinnedActions = listOf(VoiceInputAction)
val DefaultPinnedActionsString = ActionRegistry.actionsToString(DefaultPinnedActions)