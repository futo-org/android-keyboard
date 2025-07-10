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

enum class IMEMessage {
    ReloadResources
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
            if(activeIme != it && activeIme != null && inInput) {
                activeIme?.onFinishInput()
                it.onStartInput(
                    RichInputMethodManager.getInstance().currentSubtype.keyboardLayoutSetName
                )
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
        inInput = true
        getActiveIME(settings.current).onStartInput(
            RichInputMethodManager.getInstance().currentSubtype.keyboardLayoutSetName
        )
    }

    fun onFinishInput() {
        inInput = false
        getActiveIME(settings.current).onFinishInput()

        currentActionInputTransactionIME?.let { endInputTransaction(it) }
    }

    fun clearUserHistoryDictionaries() {
        // TODO: Non-active too!
        getActiveIME(settings.current).clearUserHistoryDictionaries()
    }

    private var currentActionInputTransactionIME: ActionInputTransactionIME? = null
    fun createInputTransaction(): ActionInputTransaction {
        if(currentActionInputTransactionIME != null) TODO()
        if(!inInput) TODO()

        val existingIme = getActiveIME(settings.current)
        val ime = ActionInputTransactionIME(helper)
        currentActionInputTransactionIME = ime

        existingIme.onFinishInput()

        return ime
    }

    fun endInputTransaction(inputTransactionIME: ActionInputTransactionIME) {
        if(inputTransactionIME == currentActionInputTransactionIME) {
            currentActionInputTransactionIME = null

            inputTransactionIME.ensureFinished()

            if (inInput) {
                val existingIme = getActiveIME(settings.current)
                existingIme.onStartInput(
                    RichInputMethodManager.getInstance().currentSubtype.keyboardLayoutSetName
                )
            }
        }
    }
}