package org.futo.inputmethod.engine.general

/**
 * T9Engine — multi-tap T9 med FUTO:s ordlista (Språk & modeller → Ordlista)
 * för prefix-förslag, plus TT9 för siffersekvens / bokstavskartor.
 *
 * Multi-tap är DEFAULT på varje sifferknapp:
 *   1. Sätter/cyklar bokstav i texten (t.ex. 4,4,3,3,5 → "hej").
 *   2. Bygger upp currentWord (prefix) som frågas mot FUTO DictionaryFacilitator.
 *   3. TT9 WordPredictions körs parallellt; träffar slås ihop och filtreras.
 *
 * T9 laddar ordlistan SJÄLV (ensureFutoDictionary) — beror inte på att
 * GeneralIME har körts först.
 *
 * OBS: requestUpdatingShiftState får INTE anropas i onStartInput — då är
 * KeyboardLayoutSetV2 fortfarande null → NPE och tangentbordet försvinner.
 */

import android.text.InputType
import android.text.TextUtils
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.sspanak.tt9.db.DataStore
import io.github.sspanak.tt9.ime.modes.predictions.WordPredictions
import io.github.sspanak.tt9.languages.Language
import io.github.sspanak.tt9.languages.LanguageCollection
import io.github.sspanak.tt9.preferences.settings.SettingsStore
import org.futo.inputmethod.engine.DefaultStateHint
import org.futo.inputmethod.engine.IMEHelper
import org.futo.inputmethod.engine.IMEInterface
import org.futo.inputmethod.engine.StateHint
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.keyboard.KeyboardId
import org.futo.inputmethod.latin.DictionaryFacilitator
import org.futo.inputmethod.latin.DictionaryFacilitatorProvider
import org.futo.inputmethod.latin.NgramContext
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

    private lateinit var predictions: WordPredictions
    private lateinit var settingsStore: SettingsStore
    private var currentLanguage: Language? = null

    /** FUTO binary/user-ordlista — samma typ som GeneralIME använder. */
    private val dictionaryFacilitator: DictionaryFacilitator =
        DictionaryFacilitatorProvider.getDictionaryFacilitator(false)

    /** Bokstäverna som multi-tap skrivit in (prefix för FUTO-förslag). */
    private val currentWord = StringBuilder()

    private val wordDigits = mutableListOf<Int>()
    private val digitSequence get() = wordDigits.joinToString("")

    private var lastMultiTapDigit = -1
    private var multiTapCycleIndex = 0
    private var lastMultiTapTimeMs = 0L
    private val MULTI_TAP_TIMEOUT_MS = 1200L

    private val EMOJI_ACTION_ID = 0
    private val TAG = "T9Suggest"

    private val loadingState: MutableState<Boolean> = mutableStateOf(false)
    override fun getLoadingState(): MutableState<Boolean> = loadingState

    // --- Livscykel -----------------------------------------------------

    override fun onCreate() {
        val context = helper.context
        DataStore.init(context)
        settingsStore = SettingsStore(context)
        LanguageCollection.init(context)

        predictions = WordPredictions(settingsStore).apply {
            setWordsChangedHandler { onPredictionsChanged() }
        }

        FutoDictionaryBridge.attach(dictionaryFacilitator)
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

    private val checkedLanguageIds = mutableSetOf<Int>()

    private fun ensureTt9DictionaryLoaded(language: Language) {
        val id = language.id
        if (!checkedLanguageIds.add(id)) return
        DataStore.exists({ existingIds ->
            if (!existingIds.contains(id)) {
                io.github.sspanak.tt9.db.words.DictionaryLoader.load(
                    helper.context, settingsStore, language
                )
            }
        }, arrayListOf(language))
    }

    /**
     * Ladda FUTO:s ordlista för aktiv locale (t.ex. Svenska importerad).
     * Måste köras från T9 — GeneralIME startas inte när T9 är aktiv motor.
     */
    private fun ensureFutoDictionary() {
        if (!helper.context.isDirectBootUnlocked) return
        try {
            val settings = Settings.getInstance().current
            val locales = settings.mInputAttributes.mLocaleOverride?.let { listOf(it) }
                ?: org.futo.inputmethod.latin.RichInputMethodManager.getInstance()
                    .currentSubtypeLocales

            if (dictionaryFacilitator.isForLocales(locales) &&
                dictionaryFacilitator.hasAtLeastOneInitializedMainDictionary()
            ) {
                FutoDictionaryBridge.attach(dictionaryFacilitator)
                return
            }

            dictionaryFacilitator.resetDictionaries(
                helper.context,
                locales,
                settings.mUseContactsDict,
                settings.mUsePersonalizedDicts,
                false,
                settings.mAccount,
                "",
                null
            )
            FutoDictionaryBridge.attach(dictionaryFacilitator)
            Log.d(
                TAG,
                "dict loaded=${dictionaryFacilitator.hasAtLeastOneInitializedMainDictionary()} locales=$locales"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "ensureFutoDictionary failed", t)
        }
    }

    override fun onDestroy() {
        wordDigits.clear()
        currentWord.clear()
        try {
            dictionaryFacilitator.closeDictionaries()
        } catch (_: Exception) {
        }
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
        // INTE requestUpdatingShiftState här — layout-set är null → NPE.
    }

    override fun onFinishInput() {
        finalizeWord()
        try {
            dictionaryFacilitator.onFinishInput(helper.context)
        } catch (_: Exception) {
        }
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
        // Medvetet tom — nollställning här bröt flersiffriga sekvenser.
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
        ensureTt9DictionaryLoaded(language)

        when {
            event.mKeyCode == Constants.CODE_DELETE -> {
                if (wordDigits.isNotEmpty()) {
                    connect?.deleteSurroundingText(1, 0)
                    wordDigits.removeAt(wordDigits.size - 1)
                    if (currentWord.isNotEmpty()) {
                        currentWord.deleteCharAt(currentWord.length - 1)
                    }
                    lastMultiTapDigit = -1
                    reloadSuggestions(language)
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
            if (currentWord.isNotEmpty()) {
                currentWord.deleteCharAt(currentWord.length - 1)
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
        currentWord.append(letter)
        lastMultiTapDigit = digit
        lastMultiTapTimeMs = now

        reloadSuggestions(language)
    }

    // --- Ordförslag: FUTO-prefix + TT9 ------------------------------------

    private fun reloadSuggestions(language: Language) {
        if (wordDigits.isEmpty() || currentWord.isEmpty()) {
            setNeutralSuggestionStrip()
            return
        }
        setNeutralSuggestionStrip()

        // TT9 (siffersekvens) → asynkron onPredictionsChanged
        predictions
            .setLanguage(language)
            .setDigitSequence(digitSequence)
            .load()

        // FUTO (textprefix) — visa direkt; uppdateras igen när TT9 svarar
        showMergedSuggestions(emptyList())
    }

    private fun onPredictionsChanged() {
        if (wordDigits.isEmpty()) {
            setNeutralSuggestionStrip()
            return
        }
        val tt9 = predictions.getList()
            .filter { it.isNotBlank() && it.any { ch -> ch.isLetter() } }
            .filter { !it.all { ch -> ch.isDigit() } }
        showMergedSuggestions(tt9)
    }

    private fun showMergedSuggestions(tt9Words: List<String>) {
        val prefix = currentWord.toString()
        val seqLen = wordDigits.size

        if (prefix.length < 2) {
            val letters = tt9Words
                .filter { it.length >= 2 || (seqLen == 1 && it.length == 1) }
                .distinct()
                .take(8)
            if (letters.isEmpty()) {
                setNeutralSuggestionStrip()
                return
            }
            publishStrip(letters)
            return
        }

        val futo = FutoDictionaryBridge.getPrefixSuggestions(prefix, helper, 8)
        val tt9Validated = tt9Words
            .filter { it.length >= 2 }
            .filter {
                it.startsWith(prefix, ignoreCase = true) ||
                    FutoDictionaryBridge.isValidWord(it)
            }

        val merged = (futo + tt9Validated)
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .take(8)

        Log.d(TAG, "prefix=$prefix futo=${futo.size} tt9=${tt9Words.size} merged=${merged.size} $merged")

        if (merged.isEmpty()) {
            setNeutralSuggestionStrip()
            return
        }
        publishStrip(merged)
    }

    private fun publishStrip(words: List<String>) {
        val capped = words.map { w ->
            if (shouldUppercaseLetter() && w.isNotEmpty()) {
                w.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            } else {
                w
            }
        }

        val infoList = ArrayList<SuggestedWordInfo>(capped.size)
        capped.forEachIndexed { index, word ->
            infoList.add(
                SuggestedWordInfo(
                    word,
                    "",
                    Int.MAX_VALUE - index,
                    SuggestedWordInfo.KIND_PREDICTION,
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
        val len = currentWord.length.coerceAtLeast(wordDigits.size)
        if (len > 0) {
            connect?.deleteSurroundingText(len, 0)
        }
        val out = if (shouldUppercaseLetter() && word.isNotEmpty()) {
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        } else {
            word
        }
        connect?.commitText(out, 1)

        try {
            predictions.onAccept(word, digitSequence)
        } catch (_: Exception) {
        }

        try {
            dictionaryFacilitator.addToUserHistory(
                word,
                out.firstOrNull()?.isUpperCase() == true,
                NgramContext.EMPTY_PREV_WORDS_INFO,
                System.currentTimeMillis() / 1000,
                false
            )
        } catch (_: Exception) {
        }

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
    override fun clearUserHistoryDictionaries() {
        try {
            dictionaryFacilitator.clearUserHistoryDictionary(helper.context)
            ensureFutoDictionary()
        } catch (_: Exception) {
        }
    }
    override fun requestSuggestionRefresh() {
        currentLanguage?.let { reloadSuggestions(it) } ?: onPredictionsChanged()
    }
}
