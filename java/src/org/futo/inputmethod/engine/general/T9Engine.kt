package org.futo.inputmethod.engine.general

import android.text.InputType
import android.text.TextUtils
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.sspanak.tt9.languages.Language
import io.github.sspanak.tt9.languages.LanguageCollection
import org.futo.inputmethod.engine.DefaultStateHint
import org.futo.inputmethod.engine.IMEHelper
import org.futo.inputmethod.engine.IMEInterface
import org.futo.inputmethod.engine.StateHint
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.keyboard.KeyboardId
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.WordComposer
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.InputPointers
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2
import java.util.Locale

class T9Engine(
    private val helper: IMEHelper
) : IMEInterface {

    private val connect get() = helper.getCurrentInputConnection()

    private var currentLanguage: Language? = null

    private val wordDigits = mutableListOf<Int>()
    private val currentComposingText = StringBuilder()

    private var lastMultiTapDigit = -1
    private var multiTapCycleIndex = 0
    private var lastMultiTapTimeMs = 0L
    private val MULTI_TAP_TIMEOUT_MS = 1200L

    private val EMOJI_ACTION_ID = 0

    private val loadingState: MutableState<Boolean> = mutableStateOf(false)
    override fun getLoadingState(): MutableState<Boolean> = loadingState

    // --- Livscykel -----------------------------------------------------

    override fun onCreate() {
        val context = helper.context
        LanguageCollection.init(context)
        // TT9 SQLite DataStore & DictionaryLoader är bortkopplade
        // för att använda FUTO:s importerade ordlista istället.
    }

    private fun resolveLanguage(): Language? {
        val locale = try {
            org.futo.inputmethod.latin.RichInputMethodManager.getInstance()
                .getCurrentSubtypeLocale()
        } catch (e: Exception) {
            null
        } ?: return null

        return LanguageCollection.getByLocale(locale.toLanguageTag())
            ?: LanguageCollection.getByLanguageCode(locale.language)
            ?: LanguageCollection.getByLanguageCode("sv")
    }

    override fun onDestroy() {
        wordDigits.clear()
        currentComposingText.clear()
    }

    override fun onDeviceUnlocked() {}

    override fun onStartInput() {
        wordDigits.clear()
        currentComposingText.clear()
        lastMultiTapDigit = -1
        setNeutralSuggestionStrip()
    }

    override fun onFinishInput() {
        finalizeWord()
    }

    override fun onLayoutUpdated(layout: KeyboardLayoutSetV2) {
        refreshShiftState()
    }

    override fun onOrientationChanged() {}

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        composingSpanStart: Int, composingSpanEnd: Int
    ) {
    }

    override fun isGestureHandlingAvailable(): Boolean = false
    override fun getStateHint(imeHint: String?): StateHint = DefaultStateHint

    // --- Auto-caps -------------------------------------------------------

    override fun getCurrentAutoCapsState(): Int {
        val settings = try {
            Settings.getInstance().current
        } catch (_: Exception) {
            return Constants.TextUtils.CAP_MODE_OFF
        }
        if (!settings.mAutoCap) {
            return Constants.TextUtils.CAP_MODE_OFF
        }

        val editorInfo = helper.getCurrentEditorInfo() ?: return Constants.TextUtils.CAP_MODE_OFF
        val inputType = editorInfo.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI
        ) {
            return Constants.TextUtils.CAP_MODE_OFF
        }

        val ic = connect ?: return Constants.TextUtils.CAP_MODE_OFF
        return try {
            val before = ic.getTextBeforeCursor(4, 0)?.toString() ?: ""
            when {
                before.isEmpty() -> TextUtils.CAP_MODE_WORDS
                before.endsWith(". ") || before.endsWith("! ") || before.endsWith("? ") ->
                    TextUtils.CAP_MODE_WORDS
                before.endsWith(".\n") || before.endsWith("!\n") || before.endsWith("?\n") ->
                    TextUtils.CAP_MODE_WORDS
                before.endsWith("\n") -> TextUtils.CAP_MODE_WORDS
                before.isNotEmpty() && before.last() in ".!?" -> TextUtils.CAP_MODE_WORDS
                else -> Constants.TextUtils.CAP_MODE_OFF
            }
        } catch (_: Exception) {
            Constants.TextUtils.CAP_MODE_OFF
        }
    }

    private fun shouldUppercaseLetter(): Boolean {
        val shiftMode = helper.keyboardShiftMode
        if (shiftMode != WordComposer.CAPS_MODE_OFF) {
            return true
        }
        return getCurrentAutoCapsState() != Constants.TextUtils.CAP_MODE_OFF
    }

    private fun applyCase(letter: String): String {
        return if (shouldUppercaseLetter()) {
            letter.uppercase(Locale.getDefault())
        } else {
            letter
        }
    }

    private fun refreshShiftState() {
        try {
            if (helper.keyboardSwitcher.keyboard == null) return
            helper.keyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState())
        } catch (_: Exception) {
        }
    }

    // --- Input -----------------------------------------------------------

    private fun handleFutoAction(event: Event): Boolean {
        val keyCode = event.mKeyCode

        if (keyCode in Constants.CODE_ACTION_0..Constants.CODE_ACTION_MAX) {
            finalizeWord()
            helper.triggerAction(keyCode - Constants.CODE_ACTION_0, false)
            return true
        }

        if (keyCode in Constants.CODE_ALT_ACTION_0..Constants.CODE_ALT_ACTION_MAX) {
            finalizeWord()
            helper.triggerAction(keyCode - Constants.CODE_ALT_ACTION_0, true)
            return true
        }

        return false
    }

    private fun isNumberRowPress(event: Event): Boolean {
        if (event.mCodePoint !in '0'.code..'9'.code) {
            return false
        }

        val keyboard = helper.keyboardSwitcher.keyboard
        if (keyboard?.mId?.mNumberRow != true) {
            return false
        }

        if (event.mY < 0) {
            return false
        }

        val keyboardHeight = helper.keyboardRect.height()
        if (keyboardHeight <= 0) {
            return false
        }

        return event.mY < keyboardHeight * 0.20f
    }

    private fun isOnNumberPanel(): Boolean {
        val keyboard = helper.keyboardSwitcher.keyboard ?: return false
        return keyboard.mId.mElementId == KeyboardId.ELEMENT_NUMBER
    }

    override fun onEvent(event: Event) {
        helper.requestCursorUpdate()

        when (event.eventType) {
            Event.EVENT_TYPE_INPUT_KEYPRESS,
            Event.EVENT_TYPE_INPUT_KEYPRESS_RESUMED -> {
                handleKeypress(event)
            }

            Event.EVENT_TYPE_SOFTWARE_GENERATED_STRING -> {
                handleSoftwareGeneratedText(event)
            }

            Event.EVENT_TYPE_SUGGESTION_PICKED -> {
                event.mSuggestedWordInfo?.let { replaceCurrentWordWith(it.word) }
            }

            Event.EVENT_TYPE_DOWN_UP_KEYEVENT -> {
            }

            else -> {
            }
        }
    }

    private fun handleSoftwareGeneratedText(event: Event) {
        val text = event.mText ?: return
        if (text.isEmpty()) return

        finalizeWord()
        connect?.commitText(text, 1)
        refreshShiftState()
    }

    private fun isNumericInputField(): Boolean {
        val inputType = helper.getCurrentEditorInfo()?.inputType ?: return false
        val fieldClass = inputType and InputType.TYPE_MASK_CLASS
        return fieldClass == InputType.TYPE_CLASS_NUMBER ||
            fieldClass == InputType.TYPE_CLASS_PHONE ||
            fieldClass == InputType.TYPE_CLASS_DATETIME
    }

    private fun handleKeypress(event: Event) {
        if (isNumericInputField()) {
            finalizeWord()
            if (event.isFunctionalKeyEvent()) {
                if (event.mKeyCode == Constants.CODE_DELETE) {
                    connect?.deleteSurroundingText(1, 0)
                }
                return
            }
            connect?.commitText(String(Character.toChars(event.mCodePoint)), 1)
            return
        }

        if (handleFutoAction(event)) {
            return
        }

        if (isNumberRowPress(event)) {
            finalizeWord()
            connect?.commitText(event.mCodePoint.toChar().toString(), 1)
            refreshShiftState()
            return
        }

        if (isOnNumberPanel() && event.mCodePoint in '0'.code..'9'.code) {
            finalizeWord()
            connect?.commitText(event.mCodePoint.toChar().toString(), 1)
            refreshShiftState()
            return
        }

        val language = currentLanguage ?: resolveLanguage()?.also { currentLanguage = it } ?: return

        when {
            event.mKeyCode == Constants.CODE_DELETE -> {
                if (currentComposingText.isNotEmpty()) {
                    connect?.deleteSurroundingText(1, 0)
                    currentComposingText.deleteCharAt(currentComposingText.length - 1)
                    if (wordDigits.isNotEmpty()) {
                        wordDigits.removeAt(wordDigits.size - 1)
                    }
                    lastMultiTapDigit = -1
                    reloadPredictions(language)
                } else {
                    connect?.deleteSurroundingText(1, 0)
                }
                refreshShiftState()
            }

            event.mCodePoint in '0'.code..'9'.code -> {
                handleMultiTapDigit(event.mCodePoint - '0'.code, language)
            }

            event.mCodePoint == '*'.code -> {
                finalizeWord()
                connect?.commitText("*", 1)
                refreshShiftState()
            }

            event.mCodePoint == '#'.code -> {
                finalizeWord()
                connect?.commitText("#", 1)
                refreshShiftState()
            }

            event.mCodePoint == ' '.code || event.mKeyCode == Constants.CODE_SPACE -> {
                finalizeWord()
                connect?.commitText(" ", 1)
                refreshShiftState()
            }

            event.mKeyCode == Constants.CODE_EMOJI -> {
                finalizeWord()
                helper.triggerAction(EMOJI_ACTION_ID, false)
            }

            else -> {
                if (event.isFunctionalKeyEvent()) {
                    return
                }
                finalizeWord()
                val ch = String(Character.toChars(event.mCodePoint))
                val out = if (ch.length == 1 && ch[0].isLetter()) applyCase(ch) else ch
                connect?.commitText(out, 1)
                refreshShiftState()
            }
        }
    }

    private fun handleMultiTapDigit(digit: Int, language: Language) {
        val letters = language.getKeyCharacters(digit)
        if (letters.isEmpty()) {
            finalizeWord()
            connect?.commitText(digit.toString(), 1)
            refreshShiftState()
            return
        }

        val now = System.currentTimeMillis()
        val isContinuingCycle = digit == lastMultiTapDigit &&
            (now - lastMultiTapTimeMs) < MULTI_TAP_TIMEOUT_MS

        if (isContinuingCycle) {
            multiTapCycleIndex = (multiTapCycleIndex + 1) % letters.size
            connect?.deleteSurroundingText(1, 0)
            if (currentComposingText.isNotEmpty()) {
                currentComposingText.deleteCharAt(currentComposingText.length - 1)
            }
        } else {
            multiTapCycleIndex = 0
            wordDigits.add(digit)
        }

        val raw = letters[multiTapCycleIndex]
        val letter = if (wordDigits.size <= 1 || (isContinuingCycle && wordDigits.size == 1)) {
            applyCase(raw)
        } else {
            val shiftMode = helper.keyboardShiftMode
            if (shiftMode == WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED ||
                shiftMode == WordComposer.CAPS_MODE_AUTO_SHIFT_LOCKED
            ) {
                raw.uppercase(Locale.getDefault())
            } else {
                raw
            }
        }

        connect?.commitText(letter, 1)
        currentComposingText.append(letter)
        lastMultiTapDigit = digit
        lastMultiTapTimeMs = now

        reloadPredictions(language)
    }

    /**
     * Hämtar förslag från FUTO:s inbyggda ordlista (Svenska Importerad)
     * baserat på texten som skrivs in.
     */
    private fun reloadPredictions(language: Language) {
        if (currentComposingText.isEmpty()) {
            setNeutralSuggestionStrip()
            return
        }

        try {
            val composer = WordComposer()
            val text = currentComposingText.toString()
            for (ch in text) {
                composer.add(ch.code, WordComposer.NOT_A_KEY_INDEX, WordComposer.NOT_A_COORDINATE, WordComposer.NOT_A_COORDINATE)
            }

            val keyboard = helper.keyboardSwitcher.keyboard
            val suggestedWords = helper.suggest?.getSuggestedWords(
                composer,
                null,
                keyboard,
                helper.keyboardShiftMode,
                0
            )

            if (suggestedWords != null && !suggestedWords.isEmpty) {
                helper.showSuggestionStrip(suggestedWords)
            } else {
                setNeutralSuggestionStrip()
            }
        } catch (_: Exception) {
            setNeutralSuggestionStrip()
        }
    }

    private fun replaceCurrentWordWith(word: String) {
        if (currentComposingText.isNotEmpty()) {
            connect?.deleteSurroundingText(currentComposingText.length, 0)
        }
        val out = if (shouldUppercaseLetter() && word.isNotEmpty()) {
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        } else {
            word
        }
        connect?.commitText(out, 1)
        finalizeWord()
        refreshShiftState()
    }

    private fun finalizeWord() {
        wordDigits.clear()
        currentComposingText.clear()
        lastMultiTapDigit = -1
        setNeutralSuggestionStrip()
    }

    private fun setNeutralSuggestionStrip() {
        helper.setNeutralSuggestionStrip()
    }

    // --- IMEInterface stubs -----------------------------------------------

    override fun onStartBatchInput() {}
    override fun onUpdateBatchInput(batchPointers: InputPointers?) {}
    override fun onEndBatchInput(batchPointers: InputPointers?) {}
    override fun onCancelBatchInput() {}
    override fun onCancelInput() {}
    override fun onFinishSlidingInput() {}
    override fun onCustomRequest(requestCode: Int): Boolean = false
    override fun onMovePointer(steps: Int, stepOverWords: Boolean, select: Boolean?) {}
    override fun onMoveDeletePointer(steps: Int) {}
    override fun onUpWithDeletePointerActive() {}
    override fun onUpWithPointerActive() {}
    override fun onSwipeLanguage(direction: Int) {}
    override fun onMovingCursorLockEvent(canMoveCursor: Boolean) {}
    override fun clearUserHistoryDictionaries() {}
    override fun requestSuggestionRefresh() { 
        currentLanguage?.let { reloadPredictions(it) } 
    }
}
