package org.futo.inputmethod.engine.general

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.datastore.preferences.core.stringPreferencesKey
import icu.astronot233.rime.DeployStage
import icu.astronot233.rime.Rime
import icu.astronot233.rime.RimeMessage
import icu.astronot233.rime.RimeSchema
import icu.astronot233.rime.SyncStage
import icu.astronot233.rime.X11Keys.XK_BackSpace
import icu.astronot233.rime.X11Keys.XK_Linefeed
import icu.astronot233.rime.X11Keys.XK_Return
import icu.astronot233.rime.X11Keys.XK_Tab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.futo.inputmethod.engine.ExpandableSuggestionBarConfiguration
import org.futo.inputmethod.engine.IMEHelper
import org.futo.inputmethod.engine.IMEInterface
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.Subtypes.switchToNextLanguage
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.SuggestionBlacklist
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.InputPointers
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.suggestions.SuggestionStripViewAccessor
import org.futo.inputmethod.latin.uix.FileKind
import org.futo.inputmethod.latin.uix.FloatingPreEdit
import org.futo.inputmethod.latin.uix.PreEditListener
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.actions.throwIfDebug
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.namePreferenceKeyFor
import org.futo.inputmethod.latin.uix.preferenceKeyFor
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.utils.ZipFileHelper
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object ChineseIMESettings {
    // TODO
    var schemaList: List<RimeSchema> = emptyList()
    val menu = UserSettingsMenu(
        title = R.string.chinese_settings_title,
        searchTags = R.string.chinese_setting_search_tags,
        navPath = "ime/zh", registerNavPath = true,
        settings = listOf()
    )
}

class ChineseIME(val helper: IMEHelper) : IMEInterface, SuggestionStripViewAccessor, PreEditListener {
    private val TAG = "ChineseIME (rime)"

    private val rime: Rime
    private val connect get() = helper.getCurrentInputConnection()
    private val coroScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val expandableUiCfg = ExpandableSuggestionBarConfiguration(true, false)
    private val maxBufferLength = 0x100000
    private var layoutHint: String? = null

    companion object {
        @JvmStatic
        fun getRimeDir(context: Context) = context.getExternalFilesDir("rime")
                ?: throw IllegalStateException("Failed to access ExternalFilesDir!")

        @JvmStatic
        fun getShared(context: Context) = File(getRimeDir(context), "shared")

        @JvmStatic
        fun getUser(context: Context) = File(getRimeDir(context), "user")

        @JvmStatic
        fun normalizeV(text: String) = text.replace('ü', 'v')

        val PreviouslyExtractedDictionaryName = SettingsKey(
            stringPreferencesKey("ChineseDictionaryExtractedValue"),
            ""
        )
        var localPrevExtractedDictionaryName: String? = null

        fun resetSharedFromResources(context: Context, scope: CoroutineScope): Boolean {
            val pref = FileKind.Dictionary.preferenceKeyFor("zh")
            val namePref = FileKind.Dictionary.namePreferenceKeyFor("zh")
            val filePath = context.getSetting(pref, "")
            val file = File(context.applicationContext.getExternalFilesDir(null), filePath)

            val name = context.getSetting(namePref, "?")



            if((localPrevExtractedDictionaryName ?: context.getSetting(PreviouslyExtractedDictionaryName)) == name) {
                return false
            }

            try {
                val shared = getShared(context)
                shared.walkBottomUp()
                    .fold(true) { res, it -> (it == shared || it.delete() || !it.exists()) && res }

                // We are intentionally checking this only after deleting the shared directory
                // This is because if the user deletes the dictionary file, we want to apply
                // the deletion (TODO: It doesn't apply)
                if(filePath.isEmpty()) return true
                if(!file.exists()) return true

                ZipFileHelper.extract(file, shared)

                return true
            } finally {
                localPrevExtractedDictionaryName = name
                scope.launch(Dispatchers.IO) {
                    context.setSetting(PreviouslyExtractedDictionaryName.key, name)
                }
            }
        }
    }

    init {
        val shared = getShared(helper.context)
        val user = getUser(helper.context)

        if(!shared.exists() || !user.exists()) {
            shared.mkdirs()
            user.mkdirs()
        }

        rime = Rime(shared.path, user.path, helper.context.packageName)
    }

    fun requestTakeOver(who: Any): Pair<Rime?, CoroutineScope?> {
        Log.d(TAG, "$who tries to take over")
        return Pair(rime, coroScope)
    }

    private fun handlePassByMessage(x11Code: Int, mask: Int) {
        when (x11Code) {
            XK_BackSpace -> sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL, 0)
            XK_Tab       -> sendDownUpKeyEvent(KeyEvent.KEYCODE_TAB, 0)
            XK_Linefeed  -> sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER, 0)
            XK_Return    -> sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER, 0)
            else -> connect?.commitText(String(Character.toChars(x11Code)), 1)
        }
    }

    private fun subscribeToRimeMessage() = rime.messageFlow.onEach { msg -> when (msg) {
        is RimeMessage.Deploy -> when (msg.value) {
            DeployStage.Unknown -> Log.e(TAG, "Deploy: Failed")
            DeployStage.Startup -> Log.i(TAG, "Deploy: Startup")
            DeployStage.Success -> {
                Log.i(TAG, "Deploy: Success")
                coroScope.launch {
                    ChineseIMESettings.schemaList = rime.getSchemata().also {
                        Log.d(TAG, "Schemas: ${it.joinToString { it.toString() }} ")
                    }
                }
            }
            else -> {}
        }
        is RimeMessage.Sync -> when (msg.value) {
            SyncStage.Unknown -> Log.e(TAG, "Sync: Failed")
//            SyncStage.Startup ->
//            SyncStage.Success ->
            else -> {}
        }
        is RimeMessage.Commit -> {
            connect?.commitText(msg.value, 1)
        }
        is RimeMessage.Passby -> {
            val (x11Code, mask) = msg.value
            handlePassByMessage(x11Code, mask)
        }
        is RimeMessage.Unknown -> {
            Log.e(TAG, "Unrecognized error occurred: ${msg.value}")
        }
    }}.launchIn(coroScope)

    private fun subscribeToRimePreedit() = rime.preeditFlow.onEach { ped ->
        helper.updateUiInputState(ped.isEmpty() && !inEditingState)
        helper.setPreedit(FloatingPreEdit.build(ped, this) { normalizeV(it) })
    }.launchIn(coroScope)

    var inEditingState = false
    override fun onStartEdit() {
        inEditingState = true
        coroScope.launch {
            inEditingState = true
        }
    }

    override fun onUpdateEdit(string: String) {
        coroScope.launch {
            rime.clearComposition()
            rime.simulateKeySequence(string.filter { it in 'a'..'z' || it in 'A'..'Z' })
        }
    }

    private var waitingToSelect: Pair<String, Int>? = null
    override fun onFinishEdit(string: String, candidateWord: String?, candidateIndex: Int?) {
        if(!inEditingState) return
        inEditingState = false

        coroScope.launch {
            rime.clearComposition()

            if(candidateWord != null && candidateIndex != null) {
                // TODO: This is kind of a buggy way of doing it
                waitingToSelect = candidateWord to candidateIndex
            }
            rime.simulateKeySequence(string.filter { it in 'a'..'z' || it in 'A'..'Z' })

            if(string.isEmpty()) {
                showSuggestionStrip(
                    SuggestedWords(
                        ArrayList(emptyList()),
                        ArrayList(emptyList()),
                        null,
                        false,
                        false,
                        false,
                        0,
                        0,
                        0
                    )
                )
            }
        }
    }

    private fun subscribeToRimeCandidates() = rime.candidatesFlow.onEach { cdd ->
        waitingToSelect?.let { sel ->
            if(cdd.getOrNull(sel.second)?.text == sel.first) {
                if(rime.selectCandidate(sel.second)) {
                    waitingToSelect = null
                    return@onEach
                } else {
                    Log.e(TAG, "Error selecting candidate!")
                }
            }
        }

        val suggestWordList = cdd.mapIndexed { index, candidate -> SuggestedWordInfo(
            candidate.text,
            "",
            Int.MAX_VALUE - index,
            1,
            null,
            SuggestedWordInfo.NOT_AN_INDEX,
            SuggestedWordInfo.NOT_A_CONFIDENCE,
            index,
            candidate.comment
        ) }.let { ArrayList(it) }

        if(inEditingState && suggestWordList.isEmpty()) {

        } else {
            showSuggestionStrip(
                SuggestedWords(
                    suggestWordList,
                    suggestWordList,
                    null,
                    false,
                    false,
                    false,
                    0,
                    0,
                    0
                )
            )
        }
    }.launchIn(coroScope)

    override fun onCreate() {
        if (!rime.startup(false))
            Log.e(TAG, "Error occurred!")
        subscribeToRimeMessage()
        subscribeToRimePreedit()
        subscribeToRimeCandidates()
        blacklist.init()
    }
    override fun onDestroy() {
        helper.lifecycleScope.cancel()
        coroScope.cancel()
        rime.shutdown()
    }

    override fun onStartInput() {
        if(resetSharedFromResources(helper.context, helper.lifecycleScope)) {
            prevConfiguration = null
            coroScope.launch { rime.deploy() }
        }
        updateConfig()
    }

    private fun isSimplifiedChinese(locale: Locale): Boolean {
        if (locale.language != "zh") return false
        locale.script.takeIf { it.isNotEmpty() }?.let {
            return it.equals("Hans", ignoreCase = true)
        }

        return when (locale.country.uppercase()) {
            "TW", "HK", "MO" -> false
            else             -> true
        }
    }



    private data class Configuration(
        val schema: String,
        val simplification: Boolean
    )
    private var prevConfiguration: Configuration? = null
    private fun updateConfig() {
        coroScope.launch {
            if(inEditingState) return@launch

            val locale = Settings.getInstance().current.mLocale

            val simplified = isSimplifiedChinese(locale)
            val schema = when(layoutHint) {
                "qwerty" -> {
                    when {
                        simplified -> "luna_pinyin_simp"
                        else -> "luna_pinyin"
                    }
                }
                "stroke" -> {
                    "stroke"
                }
                null -> { return@launch }
                else -> {
                    throwIfDebug(IllegalStateException("Invalid layout hint '${layoutHint}'"))
                    "luna_pinyin"
                }
            }
            val config = Configuration(schema, simplified)
            if(config != prevConfiguration) {
                rime.selectSchema(config.schema)
                rime.deploy() // TODO: Not sure this is necessary
                rime.setOption("simplification", simplified)
                rime.setOption("traditional", !simplified)
                prevConfiguration = config
            }
        }
    }

    override fun onLayoutUpdated(layout: KeyboardLayoutSetV2) {
        layoutHint = layout.mainLayout.imeHint
        if(helper.isImeActive(this)) updateConfig()
    }

    override fun onFinishInput() {
        if(inEditingState) return
        coroScope.launch {
            waitingToSelect = null
            rime.clearComposition()
        }
    }

    override fun onFinishSlidingInput() {
        // TODO("Unsupported yet")
    }

    private fun interruptInput(text: CharSequence? = null) {
        coroScope.launch {
            rime.clearComposition()
        }
        connect?.finishComposingText()
        if (text != null) {
            connect?.commitText(text, 1)
        }
    }

    private fun sendDownUpKeyEvent(keyCode: Int, metaState: Int = 0) {
        // NOTE: Modified based on InputLogic
        val eventTime = SystemClock.uptimeMillis()
        connect?.sendKeyEvent(
            KeyEvent(
                eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
        connect?.sendKeyEvent(
            KeyEvent(
                eventTime, eventTime,
                KeyEvent.ACTION_UP, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }
    private fun triggerIfIsAction(keyCode: Int): Boolean {
        if (Constants.CODE_ACTION_0 <= keyCode && keyCode <= Constants.CODE_ACTION_MAX) {
            val actionId: Int = keyCode - Constants.CODE_ACTION_0
            helper.triggerAction(actionId, false)
            return true
        }
        if (Constants.CODE_ALT_ACTION_0 <= keyCode && keyCode <= Constants.CODE_ALT_ACTION_MAX) {
            val actionId: Int = keyCode - Constants.CODE_ALT_ACTION_0
            helper.triggerAction(actionId, true)
            return true
        }
        return false
    }
    override fun onEvent(event: Event) {
        helper.requestCursorUpdate()
        when (event.eventType) {
            Event.EVENT_TYPE_INPUT_KEYPRESS,
            Event.EVENT_TYPE_INPUT_KEYPRESS_RESUMED -> {
                if (triggerIfIsAction(event.mKeyCode))
                    return
                val x11Code = when (event.mKeyCode) {
                    Constants.CODE_DELETE -> '\b'.code
                    Event.NOT_A_KEY_CODE -> event.mCodePoint
                    else -> return
                }.let { when (it) {
                    '\b'.code -> XK_BackSpace
                    '\t'.code -> XK_Tab
                    '\n'.code -> XK_Return // Use return instead of linefeed here.
                    '\r'.code -> XK_Return
                    else -> it
                } }

                if(!inEditingState) {
                    coroScope.launch { rime.processX11Code(x11Code) }
                } else {
                    handlePassByMessage(x11Code, 0)
                }
            }

            Event.EVENT_TYPE_SUGGESTION_PICKED -> {
                val suggestion = event.mSuggestedWordInfo ?: return
                if (suggestion.isKindOf(SuggestedWordInfo.KIND_UNDO)) {
                    connect?.commitText(suggestion.word, 1)
                } else {
                    coroScope.launch {
                        if(!rime.selectCandidate(suggestion.mCandidateIndex)) {
                            Log.e(TAG, "Couldn't select candidate")
                        }
                    }
                }
            }

            Event.EVENT_TYPE_SOFTWARE_GENERATED_STRING -> {
                interruptInput(event.mText)
            }

            Event.EVENT_TYPE_DOWN_UP_KEYEVENT -> {
                interruptInput()
                if (event.mX == KeyEvent.META_CTRL_ON) when (event.mKeyCode) {
                    KeyEvent.KEYCODE_F1 -> {

                    }
                    KeyEvent.KEYCODE_F2 -> {
                        Log.d(TAG, "Redeploying...")
                        coroScope.launch { rime.deploy() }
                        return
                    }
                }
                sendDownUpKeyEvent(event.mKeyCode, event.mX)
            }

            else -> {}
        }
    }

    private var anchorOutOfDate = true
    private var anchorLeftCount = 0
    private var anchorRightCount = 0
    private val currentAnchorCursor: Pair<Int, Int> get() {
        if (anchorOutOfDate) {
            anchorLeftCount = (connect?.getTextBeforeCursor(maxBufferLength, 0) ?: "").length
            anchorRightCount = (connect?.getTextAfterCursor(maxBufferLength, 0) ?: "").length
            currentMovingCursor = anchorLeftCount
            anchorOutOfDate = false
        }
        return Pair(anchorLeftCount, anchorRightCount)
    }
    private var currentMovingCursor: Int = 0
    override fun onMovePointer(steps: Int, stepOverWords: Boolean, select: Boolean?) {
        var meta = 0
        if (stepOverWords) meta = meta or KeyEvent.META_CTRL_ON
        if (select == true) meta = meta or KeyEvent.META_SHIFT_ON
        if(steps < 0) {
            for(i in 0 until -steps) {
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, meta)
            }
        } else {
            for(i in 0 until steps) {
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, meta)
            }
        }
    }
    override fun onUpWithPointerActive() {
        // TODO("Unsupported yet")
    }
    override fun onMoveDeletePointer(steps: Int) {
        setNeutralSuggestionStrip()
        val (lBound, rBound) = currentAnchorCursor
        currentMovingCursor = when {
            steps < 0 -> max(0, currentMovingCursor - 1)
            steps > 0 -> min(lBound + rBound, currentMovingCursor + 1)
            else -> currentMovingCursor
        }
        connect?.setSelection(lBound, currentMovingCursor)
    }
    override fun onUpWithDeletePointerActive() {
        anchorOutOfDate = true
        val selection: CharSequence? = connect?.getSelectedText(0)
        if (selection == null) {
            onUpWithPointerActive()
            return
        }
        coroScope.launch {
            val preedit = rime.getPreedit()
            var reserved: String
            when {
                preedit.endsWith(selection) -> {
                    reserved = preedit.removeSuffix(selection).filterNot { it.isWhitespace() }
                    connect?.commitText(reserved, 1)
                }
                selection.endsWith(preedit) -> {
                    reserved = selection.removeSuffix(preedit) as String
                    sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL, 0)
                }
                else -> {
                    sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL, 0)
                    Log.w(TAG, "Wrong preedit text fetched?")
                    return@launch
                }
            }
            rime.clearComposition()
            val info = arrayListOf(SuggestedWordInfo(
                reserved,
                "",
                Int.MAX_VALUE,
                SuggestedWordInfo.KIND_UNDO,
                null,
                0,
                0
            ))
            showSuggestionStrip(SuggestedWords(
                info,
                null,
                null,
                false,
                false,
                false,
                0,
                0
            ))
        }
    }

    override fun onSwipeLanguage(direction: Int) {
        switchToNextLanguage(helper.context, direction)
    }

    private var prevSuggest: SuggestedWords? = null
    private val blacklist = SuggestionBlacklist(Settings.getInstance(), helper.context, helper.lifecycleScope)
    override fun setNeutralSuggestionStrip() {
        prevSuggest = null
        helper.setNeutralSuggestionStrip(expandableUiCfg)
    }
    override fun showSuggestionStrip(suggestedWords: SuggestedWords?) {
        val words = suggestedWords?.let { blacklist.filterBlacklistedSuggestions(it) }
        prevSuggest = words
        helper.showSuggestionStrip(words, expandableUiCfg)
    }

    override fun requestSuggestionRefresh() {
        showSuggestionStrip(null)
    }

    override fun isGestureHandlingAvailable() : Boolean {
        return false
        // TODO("To be supported")
    }

    override fun onCustomRequest(requestCode: Int): Boolean {
        return false
        // TODO("Might never be supported")
    }

    override fun onMovingCursorLockEvent(canMoveCursor: Boolean) {
        // TODO("Might never be supported")
    }

    override fun clearUserHistoryDictionaries() {
        // TODO("Unsupported yet")
    }

    override fun onStartBatchInput() {}
    override fun onUpdateBatchInput(batchPointers: InputPointers?) {}
    override fun onEndBatchInput(batchPointers: InputPointers?) {}
    override fun onCancelBatchInput() {}

// Non-behavior methods {{
// NOTE: These methods are scheduled to have no behavior
    override fun onCancelInput() {}
    override fun onDeviceUnlocked() {}
    override fun onOrientationChanged() {}
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, composingSpanStart: Int, composingSpanEnd: Int) {}
// Non-behavior methods }}

    val debugInfo: String
        get() = "configuration=${prevConfiguration}\nlayoutHint=${layoutHint}\nlocale=${Settings.getInstance().current.mLocale}\nisSimplified=${isSimplifiedChinese(Settings.getInstance().current.mLocale)}"
}

/*TODO
    Support schema selection
*/

/*TODO: Urgency descending
    -- Developing --
    onStartInput()
    isGestureHandlingAvailable()
    onFinishSlidingInput()
    onUpWithPointerActive()
    onLayoutUpdated(layout: KeyboardLayoutSetV2)
    -- Considering --
    onMovePointer(steps: Int, stepOverWords: Boolean, select: Boolean?)
    onCustomRequest(requestCode: Int)
    onMovingCursorLockEvent(canMoveCursor: Boolean)
    clearUserHistoryDictionaries()
    -- Unscheduled --
    onStartBatchInput()
    onUpdateBatchInput(batchPointers: InputPointers?)
    onEndBatchInput(batchPointers: InputPointers?)
    onCancelBatchInput()
*/