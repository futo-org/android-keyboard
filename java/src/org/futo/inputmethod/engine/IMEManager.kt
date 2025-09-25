package org.futo.inputmethod.engine

import kotlinx.coroutines.flow.MutableSharedFlow
import org.futo.inputmethod.engine.general.ActionInputTransactionIME
import org.futo.inputmethod.engine.general.GeneralIME
import org.futo.inputmethod.engine.general.JapaneseIME
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.RichInputMethodManager
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.settings.SettingsValues
import org.futo.inputmethod.latin.uix.ActionInputTransaction
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2

enum class IMEMessage {
    ReloadResources,
    ReloadPersonalDict,
}
val GlobalIMEMessage = MutableSharedFlow<IMEMessage>(
    replay = 0,
    extraBufferCapacity = 8
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
    }

    fun onFinishInput() {
        val ime = getActiveIME(settings.current)
        inInput = false
        ime.onFinishInput()

        currentActionInputTransactionIME?.let { endInputTransaction(it) }
    }

    fun clearUserHistoryDictionaries() {
        // TODO: Should also clear in this situation:
        //  user is in English and hasn't typed in Japanese in months, but has Japanese dictionaries
        imes.values.forEach {
            it.clearUserHistoryDictionaries()
        }
    }

    private var currentActionInputTransactionIME: ActionInputTransactionIME? = null
    fun createInputTransaction(): ActionInputTransaction {
        if(currentActionInputTransactionIME != null) TODO()
        if(!inInput) TODO()

        val existingIme = getActiveIME(settings.current)
        val ime = ActionInputTransactionIME(helper)
        currentActionInputTransactionIME = ime

        if(prevSelection != null) {
            ime.onUpdateSelection(
                -1, -1,
                prevSelection!!.newSelStart,
                prevSelection!!.newSelEnd,
                prevSelection!!.composingSpanStart,
                prevSelection!!.composingSpanEnd
            )
        } else {
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

    fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        composingSpanStart: Int,
        composingSpanEnd: Int
    ) {
        prevSelection = Selection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, composingSpanStart, composingSpanEnd, currHash())
        getActiveIME(settings.current).onUpdateSelection(
            oldSelStart, oldSelEnd,
            newSelStart, newSelEnd,
            composingSpanStart, composingSpanEnd
        )
    }

    fun setLayout(layout: KeyboardLayoutSetV2) {
        imes.values.forEach { it.onLayoutUpdated(layout) }
    }
}