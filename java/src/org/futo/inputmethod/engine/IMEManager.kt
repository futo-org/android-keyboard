package org.futo.inputmethod.engine

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.annotations.UsedForTesting
import org.futo.inputmethod.engine.general.ActionInputTransactionIME
import org.futo.inputmethod.engine.general.GeneralIME
import org.futo.inputmethod.engine.general.JapaneseIME
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.settings.SettingsValues
import org.futo.inputmethod.latin.uix.ActionInputTransaction
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.actions.throwIfDebug
import org.futo.inputmethod.latin.uix.dataStore
import org.futo.inputmethod.latin.uix.deferSetSetting
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2

enum class IMEMessage {
    ReloadResources,
    ReloadPersonalDict,
}

val GlobalIMEMessage = MutableSharedFlow<IMEMessage>(
    replay = 0,
    extraBufferCapacity = 8
)

private val ImesEverUsedWithDictionaryPersonalization = SettingsKey(
    stringSetPreferencesKey("ImesEverUsedWithDictionaryPersonalization"),
    emptySet()
)

enum class IMEKind(val factory: (IMEHelper) -> IMEInterface) {
    General({ GeneralIME(it) }),
    Japanese({ JapaneseIME(it) })
}

class IMEManager(
    private val service: LatinIME,
) {
    private val helper = IMEHelper(service)
    private val settings = Settings.getInstance()
    private val imes: MutableMap<IMEKind, IMEInterface> = mutableMapOf()
    private var activeIme: IMEInterface? = null

    private fun getActiveIMEKind(settingsValues: SettingsValues): IMEKind =
        when(settingsValues.mLocale.language) {
            "ja" -> IMEKind.Japanese
            else -> IMEKind.General
        }

    private fun onImeChanged(old: IMEInterface?, new: IMEInterface) {
        if(old != null && inInput) {
            activeIme?.onFinishInput()
            startIme(new)
        }

        service.latinIMELegacy.mKeyboardSwitcher?.mainKeyboardView?.setImeAllowsGestureInput(
            new.isGestureHandlingAvailable())
    }

    fun getActiveIME(
        settingsValues: SettingsValues,
    ): IMEInterface {
        currentActionInputTransactionIME?.let { return it }

        val kind = getActiveIMEKind(settingsValues)

        if(
            settingsValues.isPersonalizationEnabled
                && !service.getSetting(ImesEverUsedWithDictionaryPersonalization).contains(kind.name)
        ) {
            service.lifecycleScope.launch(Dispatchers.IO) {
                service.dataStore.edit {
                    it[ImesEverUsedWithDictionaryPersonalization.key] =
                        (it[ImesEverUsedWithDictionaryPersonalization.key] ?: emptySet()) +
                                setOf(kind.name)
                }
            }
        }

        return imes.getOrPut(kind) {
            kind.factory(helper).also {
                if(created) it.onCreate()
            }
        }.also {
            if(activeIme != it) {
                onImeChanged(activeIme, it)
            }
            activeIme = it
        }
    }

    private var created = false
    fun onCreate() {
        created = true
        imes.forEach { it.value.onCreate() }
    }

    fun onDestroy() {
        created = false
        imes.forEach { it.value.onDestroy() }
    }

    fun onDeviceUnlocked() {
        imes.forEach { it.value.onDeviceUnlocked() }
    }

    private var inInput = false
    fun onStartInput() {
        val ime = getActiveIME(settings.current)
        inInput = true
        startIme(ime)
        prevSelection = null
    }

    fun onFinishInput() {
        val ime = getActiveIME(settings.current)
        inInput = false
        ime.onFinishInput()

        currentActionInputTransactionIME?.let { endInputTransaction(it) }
    }

    fun clearUserHistoryDictionaries() {
        if(!created) {
            throwIfDebug(IllegalStateException("Cannot clear user history dictionaries before being created."))
            return
        }

        if(!helper.context.isDirectBootUnlocked) return

        val imesToClear = service.getSetting(ImesEverUsedWithDictionaryPersonalization)
        IMEKind.entries.forEach { kind ->
            if(imesToClear.contains(kind.name)) {
                imes.getOrPut(kind) {
                    kind.factory(helper).also {
                        if (created) it.onCreate()
                    }
                }.clearUserHistoryDictionaries()
            }
        }
        service.deferSetSetting(service, ImesEverUsedWithDictionaryPersonalization.key, emptySet())
    }

    private var currentActionInputTransactionIME: ActionInputTransactionIME? = null
    fun createInputTransaction(): ActionInputTransaction {
        if(currentActionInputTransactionIME != null) {
            throwIfDebug(IllegalStateException("Cannot create an input transaction while one is already active."))
            endInputTransaction(currentActionInputTransactionIME!!)
        }
        if(!inInput) {
            throwIfDebug(IllegalStateException("Cannot create an input transaction while outside of input."))
        }

        val existingIme = getActiveIME(settings.current)
        val ime = ActionInputTransactionIME(helper)
        currentActionInputTransactionIME = ime

        var selectionUpdated = false
        prevSelection?.apply {
            if(currHash() == hash) {
                ime.onUpdateSelection(
                    -1, -1,
                    newSelStart,
                    newSelEnd,
                    composingSpanStart,
                    composingSpanEnd
                )
                selectionUpdated = true
            }
        }

        if(!selectionUpdated) {
            ime.onUpdateSelection(
                -1, -1,
                helper.getCurrentEditorInfo()?.initialSelStart ?: -1,
                helper.getCurrentEditorInfo()?.initialSelEnd ?: -1,
                -1, -1
            )
        }

        existingIme.onFinishInput()

        return ime
    }

    private fun startIme(ime: IMEInterface) {
        ime.onStartInput()

        // We need to apply previous selection in the event of a switch, because IC.requestCursorUpdates isn't always reliable
        prevSelection?.apply {
            if(currHash() == hash) {
                ime.onUpdateSelection(
                    oldSelStart,
                    oldSelEnd,
                    newSelStart,
                    newSelEnd,
                    composingSpanStart,
                    composingSpanEnd
                )
            }
        }
    }

    fun endInputTransaction(inputTransactionIME: ActionInputTransactionIME) {
        if(inputTransactionIME == currentActionInputTransactionIME) {
            currentActionInputTransactionIME = null

            inputTransactionIME.ensureFinished()

            if (inInput) {
                val existingIme = getActiveIME(settings.current)
                startIme(existingIme)
            }
        }
    }

    data class Selection(
        val oldSelStart: Int,
        val oldSelEnd: Int,
        val newSelStart: Int,
        val newSelEnd: Int,
        val composingSpanStart: Int,
        val composingSpanEnd: Int,
        val hash: Int
    )

    private var prevSelection: Selection? = null

    private fun currHash(): Int {
        return (helper.getCurrentEditorInfo()?.hashCode() ?: 0) xor (helper.getCurrentInputConnection()?.hashCode() ?: 0)
    }

    private var pendingUpdateSelection: Pair<Job, Selection>? = null
    fun ensureUpdateSelectionFinished() {
        pendingUpdateSelection?.let {
            it.first.cancel()

            val s = it.second
            getActiveIME(settings.current).onUpdateSelection(
                s.oldSelStart, s.oldSelEnd,
                s.newSelStart, s.newSelEnd,
                s.composingSpanStart, s.composingSpanEnd
            )
        }
        pendingUpdateSelection = null
    }

    fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        composingSpanStart: Int,
        composingSpanEnd: Int
    ) {
        val sel = Selection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, composingSpanStart, composingSpanEnd, currHash())
        pendingUpdateSelection?.first?.cancel()
        pendingUpdateSelection = service.lifecycleScope.launch {
            delay(20L)
            withContext(Dispatchers.Main) {
                prevSelection = sel
                getActiveIME(settings.current).onUpdateSelection(
                    oldSelStart, oldSelEnd,
                    newSelStart, newSelEnd,
                    composingSpanStart, composingSpanEnd
                )
                pendingUpdateSelection = null
            }
        } to sel
    }

    fun setLayout(layout: KeyboardLayoutSetV2) {
        imes.values.forEach { it.onLayoutUpdated(layout) }
    }

    @UsedForTesting
    fun recycle() {
        imes.values.forEach { it.recycle() }
    }
}