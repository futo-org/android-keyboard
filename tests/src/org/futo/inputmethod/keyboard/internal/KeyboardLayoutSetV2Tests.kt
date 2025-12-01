package org.futo.inputmethod.keyboard.internal

import android.graphics.Rect
import android.test.AndroidTestCase
import android.view.inputmethod.EditorInfo
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2Params
import org.futo.inputmethod.v2keyboard.LayoutManager
import org.futo.inputmethod.v2keyboard.RegularKeyboardSize
import java.util.Locale

class KeyboardLayoutSetV2Tests : AndroidTestCase() {
    private val layoutParams = KeyboardLayoutSetV2Params(
        computedSize = RegularKeyboardSize(1024, 1024, Rect()),
        keyboardLayoutSet = "qwerty",
        locale = Locale.ENGLISH,
        editorInfo = EditorInfo(),
        numberRow = false,
        arrowRow = false,
        bottomActionKey = null,
        multilingualTypingLocales = emptyList(),
        numberRowMode = 0,
        useLocalNumbers = false,
        alternativePeriodKey = false
    )

    private fun getActualHeight(layoutSet: KeyboardLayoutSetV2): Int {
        return layoutSet.getKeyboard(
            KeyboardLayoutElement(
                kind = KeyboardLayoutKind.Alphabet0,
                page = KeyboardLayoutPage.Base
            )
        ).mBaseHeight
    }

    fun testKeyboardHeightSettingAffectsHeight() {
        LayoutManager.init(context)
        val testHeight = { tgtHeight: Int -> assertEquals(getActualHeight(KeyboardLayoutSetV2(context, layoutParams.copy(computedSize = RegularKeyboardSize(1024, tgtHeight, Rect())))), tgtHeight) }

        testHeight(600)
        testHeight(1200)
        testHeight(67)
        testHeight(185)
        testHeight(4440)
    }
}