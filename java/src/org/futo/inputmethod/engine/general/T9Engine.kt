package org.futo.inputmethod.engine.general

/**
 * T9Engine — multi-tap + ordförslag från FUTO:s ordlista (prefix).
 *
 * Multi-tap skriver bokstäver direkt. Förslag hämtas via FutoDictionaryBridge
 * mot samma DictionaryFacilitator som GeneralIME (Språk & modeller → Ordlista).
 * TT9 Language används bara för digit→bokstav (getKeyCharacters).
 */

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
import org.futo.inputmethod.latin.DictionaryFacilitator
import org.futo.inputmethod.latin.DictionaryFacilitatorProvider
import org.futo.inputmethod.latin.NgramContext
import org.futo.inputmethod.latin.RichInputMethodManager
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.WordComposer
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.InputPointers
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2
import java.util.Locale

class T9Engine(
    private val helper: IMEHelper
) : IMEInterface {

    private val connect get() = helper.getCurrentInputConnection()

    /** Egen facilitator om GeneralIME inte hunnit attach:a (T9 som enda motor). */
    private val localDictionary: DictionaryFacilitator =
        DictionaryFacilitatorProvider.getDictionaryFacilitator(false)

    private var currentLanguage: Language? = null

    private val wordDigits = mutableListOf<Int>()
    /** Det multi-tap skrivit hittills (prefix för FUTO-ordlistan). */
    private val currentWord = StringBuilder()

    private var lastMultiTapDigit = -1
    private var multiTapCycleIndex = 0
    private var lastMultiTapTimeMs = 0L
    private val MULTI_TAP_TIMEOUT_MS = 1200L

    private val EMOJI_ACTION_ID = 0

    private val loadingState: MutableState<Boolean> = mutableStateOf(false)
    override fun getLoadingState(): MutableState<Boolean> = loadingState

    override fun onCreate() {
        LanguageCollection.init(helper.context)
        ensureFutoDictionary()
    }

    private fun ensureFutoDictionary() {
        if (!helper.context.isDirectBootUnlocked) return
        // Preferera GeneralIME:s delade facilitator om den finns
        if (FutoDictionaryBridge.get() != null) return

        val settings = Settings.getInstance().current
        val locales = settings.mInputAttributes.mLocaleOverride?.let { listOf(it) }
            ?: RichInputMethodManager.getInstance().currentSubtypeLocales

        localDictionary.resetDictionaries(
            helper.context,
            locales,
            settings.mUseContactsDict,
            settings.mUsePersonalizedDicts,
            false,
            settings.mAccount,
            "",
            null
        )
        FutoDictionaryBridge.attach(localDictionary)
    }

    private fun resolveLanguage(): Language? {
        val locale = try {
            RichInputMethodManager.getInstance().getCurrentSubtypeLocale()
        } catch (_: Exception) {
            null
        } ?: return null

        return LanguageCollection.getByLocale(locale.toLanguageTag())
            ?: LanguageCollection.getByLanguageCode(locale.language)
            ?: LanguageCollection.getByLanguageCode("sv")
    }

    override fun onDestroy() {
        wordDigits.clear()
        currentWord.clear()
    }

    override fun onDeviceUnlocked() {
        ensureFutoDictionary()
    }

    override fun onStartInput() {
        wordDigits.clear()
        currentWord.clear()
        lastMultiTapDigit = -1
        setNeutralSuggestionStrip()
        ensureFutoDictionary()
        helper.keyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState())
    }

    override fun onFinishInput() {
        finalizeWord()
    }

    override fun onLayoutUpdated(layout: KeyboardLayoutSetV2) {}
    override fun onOrientationChanged() {}

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        composingSpanStart: Int, composingSpanEnd: Int
    ) {}

    override fun isGestureHandlingAvailable(): Boolean = false
    override fun getStateHint(imeHint: String?): StateHint = DefaultStateHint

    // --- Auto-caps -------------------------------------------------------

    override fun getCurrentAutoCapsState(): Int {
        val settings = try {
            Settings.getInstance().current
        } catch (_: Exception) {
            return Constants.TextUtils.CAP_MODE_OFF
        }
        if (!settings.mAutoCap) return Constants.TextUtils.CAP_MODE_OFF

        val editorInfo = helper.getCurrentEditorInfo() ?: return Constants.TextUtils.CAP_MODE_OFF
        val variation = editorInfo.inputType and InputType.TYPE_MASK_VARIATION
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
        if (helper.keyboardShiftMode != WordComposer.CAPS_MODE_OFF) return true
        return getCurrentAutoCapsState() != Constants.TextUtils.CAP_MODE_OFF
    }

    private fun applyCase(letter: String): String =
        if (shouldUppercaseLetter()) letter.uppercase(Locale.getDefault()) else letter

    private fun refreshShiftState() {
        try {
            helper.keyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState())
        } catch (_: Exception) {}
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
        if (event.mCodePoint !in '0'.code..'9'.code) return false
        val keyboard = helper.keyboardSwitcher.keyboard
        if (keyboard?.mId?.mNumberRow != true) return false
        if (event.mY < 0) return false
        val keyboardHeight = helper.keyboardRect.height()
        if (keyboardHeight <= 0) return false
        return event.mY < keyboardHeight * 0.20f
    }

    private fun isOnNumberPanel(): Boolean {
        val keyboard = helper.keyboardSwitcher.keyboard ?: return false
        return keyboard.mId.mElementId == KeyboardId.ELEMENT_NUMBER
    }

    private fun isNumericInputField(): Boolean {
        val inputType = helper.getCurrentEditorInfo()?.inputType ?: return false
        val fieldClass = inputType and InputType.TYPE_MASK_CLASS
        return fieldClass == InputType.TYPE_CLASS_NUMBER ||
            fieldClass == InputType.TYPE_CLASS_PHONE ||
            fieldClass == InputType.TYPE_CLASS_DATETIME
    }

    override fun onEvent(event: Event) {
        helper.requestCursorUpdate()
        when (event.eventType) {
            Event.EVENT_TYPE_INPUT_KEYPRESS,
            Event.EVENT_TYPE_INPUT_KEYPRESS_RESUMED -> handleKeypress(event)
            Event.EVENT_TYPE_SOFTWARE_GENERATED_STRING -> handleSoftwareGeneratedText(event)
            Event.EVENT_TYPE_SUGGESTION_PICKED ->
                event.mSuggestedWordInfo?.let { replaceCurrentWordWith(it.word) }
            else -> {}
        }
    }

    private fun handleSoftwareGeneratedText(event: Event) {
        val text = event.mText ?: return
        if (text.isEmpty()) return
        finalizeWord()
        connect?.commitText(text, 1)
        refreshShiftState()
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

        if (handleFutoAction(event)) return

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
                if (currentWord.isNotEmpty()) {
                    connect?.deleteSurroundingText(1, 0)
                    currentWord.deleteCharAt(currentWord.length - 1)
                    if (wordDigits.isNotEmpty()) wordDigits.removeAt(wordDigits.size - 1)
                    lastMultiTapDigit = -1
                    reloadFutoSuggestions()
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
                // Lär in ordet i FUTO user history om möjligt
                if (currentWord.isNotEmpty()) {
                    try {
                        FutoDictionaryBridge.get()?.addToUserHistory(
                            currentWord.toString(),
                            currentWord.first().isUpperCase(),
                            NgramContext.EMPTY_PREV_WORDS_INFO,
                            System.currentTimeMillis() / 1000,
                            true
                        )
                    } catch (_: Exception) {}
                }
                finalizeWord()
                connect?.commitText(" ", 1)
                refreshShiftState()
            }

            event.mKeyCode == Constants.CODE_EMOJI -> {
                finalizeWord()
                helper.triggerAction(EMOJI_ACTION_ID, false)
            }

            else -> {
                if (event.isFunctionalKeyEvent()) return
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
            if (currentWord.isNotEmpty()) currentWord.deleteCharAt(currentWord.length - 1)
        } else {
            multiTapCycleIndex = 0
            wordDigits.add(digit)
        }

        val raw = letters[multiTapCycleIndex]
        val letter = if (currentWord.isEmpty()) {
            applyCase(raw)
        } else {
            val shiftMode = helper.keyboardShiftMode
            if (shiftMode == WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED ||
                shiftMode == WordComposer.CAPS_MODE_AUTO_SHIFT_LOCKED
            ) {
                raw.uppercase(Locale.getDefault())
            } else {
                raw.lowercase(Locale.getDefault())
            }
        }

        connect?.commitText(letter, 1)
        currentWord.append(letter.lowercase(Locale.getDefault()))
        lastMultiTapDigit = digit
        lastMultiTapTimeMs = now

        reloadFutoSuggestions()
    }

    /** Ordförslag från FUTO:s ordlista utifrån multi-tap-prefix. */
    private fun reloadFutoSuggestions() {
        val prefix = currentWord.toString()
        if (prefix.length < 2) {
            setNeutralSuggestionStrip()
            return
        }

        setNeutralSuggestionStrip()

        val words = FutoDictionaryBridge.getPrefixSuggestions(prefix, helper)
        if (words.isEmpty()) {
            setNeutralSuggestionStrip()
            return
        }

        val capped = words.map { w ->
            if (shouldUppercaseLetter() && w.isNotEmpty()) {
                w.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            } else w
        }

        val infoList = ArrayList<SuggestedWordInfo>(capped.size)
        capped.forEachIndexed { index, word ->
            infoList.add(
                SuggestedWordInfo(
                    word,
                    prefix,
                    Int.MAX_VALUE - index,
                    SuggestedWordInfo.KIND_COMPLETION,
                    null,
                    SuggestedWordInfo.NOT_AN_INDEX,
                    SuggestedWordInfo.NOT_A_CONFIDENCE
                )
            )
        }

        helper.showSuggestionStrip(
            SuggestedWords(infoList, infoList, null, false, false, false, 0, 0)
        )
    }

    private fun replaceCurrentWordWith(word: String) {
        val len = currentWord.length
        if (len > 0) connect?.deleteSurroundingText(len, 0)

        val out = if (shouldUppercaseLetter() && word.isNotEmpty()) {
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        } else word

        connect?.commitText(out, 1)

        try {
            FutoDictionaryBridge.get()?.addToUserHistory(
                word,
                out.firstOrNull()?.isUpperCase() == true,
                NgramContext.EMPTY_PREV_WORDS_INFO,
                System.currentTimeMillis() / 1000,
                true
            )
        } catch (_: Exception) {}

        wordDigits.clear()
        currentWord.clear()
        lastMultiTapDigit = -1
        setNeutralSuggestionStrip()
        refreshShiftState()
    }

    private fun finalizeWord() {
        wordDigits.clear()
        currentWord.clear()
        lastMultiTapDigit = -1
        setNeutralSuggestionStrip()
    }

    private fun setNeutralSuggestionStrip() {
        helper.setNeutralSuggestionStrip()
    }

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
    override fun clearUserHistoryDictionaries() {
        FutoDictionaryBridge.get()?.clearUserHistoryDictionary(helper.context)
    }
    override fun requestSuggestionRefresh() { reloadFutoSuggestions() }
}
