package org.futo.inputmethod.keyboard.internal

import android.graphics.Rect
import android.test.AndroidTestCase
import android.view.inputmethod.EditorInfo
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.latin.settings.LongPressKey
import org.futo.inputmethod.latin.settings.LongPressKeySettings
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2Params
import org.futo.inputmethod.v2keyboard.LayoutManager
import org.futo.inputmethod.v2keyboard.RegularKeyboardSize
import java.util.Locale

class LongPressKeysLayoutTests : AndroidTestCase() {
    private val affectedLayouts = listOf("clearflow", "kasroz")
    private val actionIcons = setOf(
        "action_select_all",
        "action_undo",
        "action_redo",
        "action_cut",
        "action_copy",
        "action_paste"
    )

    private fun buildKeyboard(
        layout: String,
        groups: List<LongPressKey>,
        showHints: Boolean = false
    ): Keyboard {
        val params = KeyboardLayoutSetV2Params(
            computedSize = RegularKeyboardSize(1024, 1024, Rect()),
            keyboardLayoutSet = layout,
            locale = Locale.ENGLISH,
            editorInfo = EditorInfo(),
            numberRow = false,
            arrowRow = false,
            bottomActionKey = null,
            multilingualTypingLocales = emptyList(),
            numberRowMode = 0,
            useLocalNumbers = false,
            alternativePeriodKey = false,
            longPressKeySettings = LongPressKeySettings(groups, showHints)
        )

        return KeyboardLayoutSetV2(context, params).getKeyboard(
            KeyboardLayoutElement(KeyboardLayoutKind.Alphabet0, KeyboardLayoutPage.Base)
        )
    }

    private fun collectMoreKeys(keyboard: Keyboard): List<MoreKeySpec> =
        keyboard.sortedKeys.flatMap { it.moreKeys }

    private fun assertForAffectedLayouts(
        groups: List<LongPressKey>,
        showHints: Boolean = false,
        assertions: (String, List<MoreKeySpec>) -> Unit
    ) {
        affectedLayouts.forEach { layout ->
            assertions(layout, collectMoreKeys(buildKeyboard(layout, groups, showHints)))
        }
    }

    private fun labels(moreKeys: List<MoreKeySpec>): Set<String> =
        moreKeys.flatMap { listOfNotNull(it.mLabel, it.mOutputText) }.toSet()

    private fun icons(moreKeys: List<MoreKeySpec>): Set<String> =
        moreKeys.mapNotNull { it.mIconId }.toSet()

    fun testQuickActionsAreGenerated() {
        assertForAffectedLayouts(listOf(LongPressKey.QuickActions)) { layout, moreKeys ->
            assertTrue("$layout is missing quick actions", icons(moreKeys).containsAll(actionIcons))
        }
    }

    fun testNumbersAreGenerated() {
        val expected = "1234567890".map { it.toString() }.toSet()

        assertForAffectedLayouts(listOf(LongPressKey.Numbers)) { layout, moreKeys ->
            assertTrue("$layout is missing number long-press keys", labels(moreKeys).containsAll(expected))
        }
    }

    fun testSymbolsAreGenerated() {
        val expected = setOf("@", "*", ":")

        assertForAffectedLayouts(listOf(LongPressKey.Symbols)) { layout, moreKeys ->
            assertTrue("$layout is missing symbol long-press keys", labels(moreKeys).containsAll(expected))
        }
    }

    fun testMiscLettersAreGenerated() {
        assertForAffectedLayouts(listOf(LongPressKey.MiscLetters)) { layout, moreKeys ->
            assertTrue("$layout is missing misc letter ß", labels(moreKeys).contains("ß"))
        }
    }

    fun testNumberHintsAreGenerated() {
        assertForAffectedLayouts(listOf(LongPressKey.Numbers, LongPressKey.Symbols), showHints = true) { layout, moreKeys ->
            val hints = moreKeys.mapNotNull { it.mLabel }.toSet()
            assertTrue("$layout is missing number hints", hints.any { it in "1234567890" })
        }

        affectedLayouts.forEach { layout ->
            val keyboard = buildKeyboard(layout, listOf(LongPressKey.Numbers, LongPressKey.Symbols), true)
            val hints = keyboard.sortedKeys.mapNotNull { it.effectiveHintLabel }.toSet()
            assertTrue("$layout is missing visible number hints", hints.any { it in "1234567890" })

            keyboard.sortedKeys.filter { it.effectiveHintLabel != null }.forEach { key ->
                val keyLabels = key.moreKeys.mapNotNull { it.mLabel }.toSet()
                val hint = key.effectiveHintLabel!!
                assertTrue("$layout has a stale hint", hint in keyLabels)
            }
        }
    }

    fun testDisabledQuickActionsStayDisabled() {
        assertForAffectedLayouts(emptyList()) { layout, moreKeys ->
            assertTrue("$layout unexpectedly contains quick actions", icons(moreKeys).intersect(actionIcons).isEmpty())
        }
    }

    fun testQwertyControl() {
        val moreKeys = collectMoreKeys(buildKeyboard("qwerty", listOf(LongPressKey.QuickActions)))

        assertTrue("qwerty is missing quick actions", icons(moreKeys).containsAll(actionIcons))
    }

    override fun setUp() {
        super.setUp()
        LayoutManager.init(context)
    }
}
