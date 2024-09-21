package org.futo.inputmethod.keyboard.internal

import android.graphics.Rect
import android.test.AndroidTestCase
import android.view.inputmethod.EditorInfo
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.uix.KeyboardHeightMultiplierSetting
import org.futo.inputmethod.latin.uix.dataStore
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2Params
import java.util.Locale
import kotlin.math.absoluteValue

class KeyboardLayoutSetV2Tests : AndroidTestCase() {
    private val layoutParams = KeyboardLayoutSetV2Params(
        width = 1024,
        height = 1024,
        padding = Rect(),
        keyboardLayoutSet = "qwerty",
        locale = Locale.ENGLISH,
        editorInfo = EditorInfo(),
        numberRow = false,
        useSplitLayout = false,
        splitLayoutWidth = 0,
        bottomActionKey = null
    )

    private fun setHeight(to: Float) {
        runBlocking {
            context.dataStore.edit { it[KeyboardHeightMultiplierSetting.key] = to }
        }
        KeyboardLayoutSetV2.onKeyboardThemeChanged(context)
    }

    private fun resetHeight() {
        runBlocking {
            context.dataStore.edit { it.remove(KeyboardHeightMultiplierSetting.key) }
        }
        KeyboardLayoutSetV2.onKeyboardThemeChanged(context)
    }

    private fun getActualHeight(layoutSet: KeyboardLayoutSetV2): Int {
        return layoutSet.getKeyboard(
            KeyboardLayoutElement(
                kind = KeyboardLayoutKind.Alphabet,
                page = KeyboardLayoutPage.Base
            )
        ).mBaseHeight
    }

    fun testKeyboardHeightSettingAffectsHeight() {
        try {
            val layoutSet = KeyboardLayoutSetV2(context, layoutParams)

            // Allow for 1px rounding error
            val eps = 1.0f

            setHeight(1.0f)
            val baseHeight = getActualHeight(layoutSet)

            setHeight(2.0f)
            assert((2.0f * baseHeight - getActualHeight(layoutSet)).absoluteValue < eps)

            setHeight(0.5f)
            assert((0.5f * baseHeight - getActualHeight(layoutSet)).absoluteValue < eps)
        } finally {
            resetHeight()
        }
    }
}