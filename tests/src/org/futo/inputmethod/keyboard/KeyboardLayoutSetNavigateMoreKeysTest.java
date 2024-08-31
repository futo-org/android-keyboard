/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.futo.inputmethod.keyboard;

import android.test.suitebuilder.annotation.SmallTest;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import org.futo.inputmethod.keyboard.internal.KeyboardIconsSet;
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutElement;
import org.futo.inputmethod.keyboard.internal.MoreKeySpec;
import org.futo.inputmethod.latin.RichInputMethodManager;
import org.futo.inputmethod.latin.Subtypes;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils;
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2;

import java.util.List;
import java.util.Locale;

@SmallTest
public class KeyboardLayoutSetNavigateMoreKeysTest extends KeyboardLayoutSetTestsBase {
    private ExpectedMoreKey mSwitchLanguageMoreKey = new ExpectedMoreKey("action_switch_language");
    private ExpectedMoreKey mTextEditMoreKey = new ExpectedMoreKey("action_text_edit");
    private ExpectedMoreKey mClipboardHistoryMoreKey = new ExpectedMoreKey("action_clipboard_history");
    private ExpectedMoreKey mEmojiMoreKey = new ExpectedMoreKey("action_emoji");
    private ExpectedMoreKey mExpectedNavigateNextMoreKey = new ExpectedMoreKey(KeyboardIconsSet.NAME_NEXT_KEY);
    private ExpectedMoreKey mExpectedNavigatePreviousMoreKey = new ExpectedMoreKey(KeyboardIconsSet.NAME_PREVIOUS_KEY);
    private ExpectedMoreKey mUndoMoreKey = new ExpectedMoreKey("action_undo");
    private ExpectedMoreKey mRedoMoreKey = new ExpectedMoreKey("action_redo");

    private final int mEnterMoreKeysExpectedColumnCount = 4;

    /**
     * This class represents an expected more key.
     */
    protected static class ExpectedMoreKey {
        public static final int NO_LABEL = 0;
        public static final ExpectedMoreKey[] EMPTY_MORE_KEYS = new ExpectedMoreKey[0];

        public final int mLabelResId;
        public final String mIconId;

        public ExpectedMoreKey(final String iconName) {
            mLabelResId = NO_LABEL;
            mIconId = iconName;
        }

        public ExpectedMoreKey(final int labelResId) {
            mLabelResId = labelResId;
            mIconId = KeyboardIconsSet.ICON_UNDEFINED;
        }
    }

    private void doTestMoreKeysOf(final int code, final InputMethodSubtype subtype,
            final int elementId, final int inputType, final int imeOptions,
            final ExpectedMoreKey ... expectedMoreKeys) {
        final EditorInfo editorInfo = new EditorInfo();
        editorInfo.inputType = inputType;
        editorInfo.imeOptions = imeOptions;
        final KeyboardLayoutSetV2 layoutSet = createKeyboardLayoutSet(subtype, editorInfo);
        final Keyboard keyboard = layoutSet.getKeyboard(KeyboardLayoutElement.fromElementId(elementId));

        final Key actualKey = keyboard.getKey(code);
        final List<MoreKeySpec> actualMoreKeys = actualKey.getMoreKeys();
        final String tag = actualKey.toString() + " moreKeys=" + actualMoreKeys.toString();
        if (expectedMoreKeys.length == 0) {
            assertEquals(tag, 0, actualMoreKeys.size());
            return;
        }
        if (expectedMoreKeys.length == 1) {
            assertFalse(tag + " fixedOrder", actualKey.isMoreKeysFixedOrder());
            assertFalse(tag + " fixedColumn", actualKey.isMoreKeysFixedColumn());
        } else {
            assertTrue(tag + " fixedOrder", actualKey.isMoreKeysFixedOrder());
            assertTrue(tag + " fixedColumn", actualKey.isMoreKeysFixedColumn());
            assertEquals(tag + " column",
                    mEnterMoreKeysExpectedColumnCount, actualKey.getMoreKeysColumnNumber());
        }
        assertNotNull(tag + " moreKeys", actualMoreKeys);
        assertEquals(tag, expectedMoreKeys.length, actualMoreKeys.size());
        for (int index = 0; index < actualMoreKeys.size(); index++) {
            final int expectedLabelResId = expectedMoreKeys[index].mLabelResId;
            if (expectedLabelResId == ExpectedMoreKey.NO_LABEL) {
                assertEquals(tag + " label " + index, null, actualMoreKeys.get(index).mLabel);
            } else {
                final CharSequence expectedLabel = getContext().getText(expectedLabelResId);
                assertEquals(tag + " label " + index, expectedLabel, actualMoreKeys.get(index).mLabel);
            }
            final String expectedIconId = expectedMoreKeys[index].mIconId;
            assertEquals(tag + " icon " + index, expectedIconId, actualMoreKeys.get(index).mIconId);
        }
    }

    // Updated from AOSP:
    //  1. Several quick actions are always on enter key
    //  2. Both next and previous are always there if either one is meant to ever be shown
    private void doTestNavigationMoreKeysOf(final int code, final InputMethodSubtype subtype,
            final int elementId, final int inputType) {

        ExpectedMoreKey[] normal = new ExpectedMoreKey[] {
                mSwitchLanguageMoreKey,
                mTextEditMoreKey,
                mClipboardHistoryMoreKey,
                mEmojiMoreKey,
                mUndoMoreKey,
                mRedoMoreKey
        };

        ExpectedMoreKey[] withNav = new ExpectedMoreKey[] {
                mSwitchLanguageMoreKey,
                mTextEditMoreKey,
                mClipboardHistoryMoreKey,
                mEmojiMoreKey,
                mExpectedNavigatePreviousMoreKey,
                mExpectedNavigateNextMoreKey,
                mUndoMoreKey,
                mRedoMoreKey
        };

        // No navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_NULL,
                normal);
        // With next navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_FLAG_NAVIGATE_NEXT,
                withNav);
        // With previous navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                withNav);
        // With next and previous naviagte flags.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_FLAG_NAVIGATE_NEXT | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                withNav);
        // Action next.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT,
                withNav);
        // Action next with next navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NAVIGATE_NEXT,
                withNav);
        // Action next with previous navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                withNav);
        // Action next with next and previous navigate flags.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NAVIGATE_NEXT
                        | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                withNav);
        // Action previous.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS,
                withNav);
        // Action previous with next navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS | EditorInfo.IME_FLAG_NAVIGATE_NEXT,
                withNav);
        // Action previous with previous navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                withNav);
        // Action previous with next and previous navigate flags.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS | EditorInfo.IME_FLAG_NAVIGATE_NEXT
                        | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                withNav);
    }

    public void testMoreKeysOfEnterKey() {
        final RichInputMethodManager richImm = RichInputMethodManager.getInstance();
        final InputMethodSubtype subtype = Subtypes.INSTANCE.makeSubtype(
                Locale.US.toString(), SubtypeLocaleUtils.QWERTY);

        // Password field.
        doTestNavigationMoreKeysOf(Constants.CODE_ENTER, subtype, KeyboardId.ELEMENT_ALPHABET,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        // Email field.
        doTestNavigationMoreKeysOf(Constants.CODE_ENTER, subtype, KeyboardId.ELEMENT_ALPHABET,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        // Url field.
        doTestNavigationMoreKeysOf(Constants.CODE_ENTER, subtype, KeyboardId.ELEMENT_ALPHABET,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        // Phone number field.
        doTestNavigationMoreKeysOf(Constants.CODE_ENTER, subtype, KeyboardId.ELEMENT_PHONE,
                InputType.TYPE_CLASS_PHONE);
        // Number field.
        doTestNavigationMoreKeysOf(Constants.CODE_ENTER, subtype, KeyboardId.ELEMENT_NUMBER,
                InputType.TYPE_CLASS_NUMBER);
        // Date-time field.
        doTestNavigationMoreKeysOf(Constants.CODE_ENTER, subtype, KeyboardId.ELEMENT_NUMBER,
                InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_NORMAL);
        // Date field.
        doTestNavigationMoreKeysOf(Constants.CODE_ENTER, subtype, KeyboardId.ELEMENT_NUMBER,
                InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        // Time field.
        doTestNavigationMoreKeysOf(Constants.CODE_ENTER, subtype, KeyboardId.ELEMENT_NUMBER,
                InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        // Text field.
        doTestNavigationMoreKeysOf(Constants.CODE_ENTER, subtype, KeyboardId.ELEMENT_ALPHABET,
                    InputType.TYPE_CLASS_TEXT);

        // Short message field.
        doTestNavigationMoreKeysOf(Constants.CODE_ENTER, subtype,
                KeyboardId.ELEMENT_ALPHABET,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
    }

    @Override
    protected int getKeyboardThemeForTests() {
        return KeyboardTheme.THEME_ID_LXX_LIGHT;
    }
}
