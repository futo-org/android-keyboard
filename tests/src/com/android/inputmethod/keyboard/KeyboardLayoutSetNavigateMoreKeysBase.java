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

package com.android.inputmethod.keyboard;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.MoreKeySpec;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Arrays;
import java.util.Locale;

abstract class KeyboardLayoutSetNavigateMoreKeysBase extends KeyboardLayoutSetTestsBase {
    private ExpectedMoreKey mExpectedNavigateNextMoreKey;
    private ExpectedMoreKey mExpectedNavigatePreviousMoreKey;
    private ExpectedMoreKey mExpectedEmojiMoreKey;

    protected ExpectedMoreKey getExpectedNavigateNextMoreKey() {
        return new ExpectedMoreKey(R.string.label_next_key);
    }

    protected ExpectedMoreKey getExpectedNavigatePreviousMoreKey() {
        return new ExpectedMoreKey(R.string.label_previous_key);
    }

    protected ExpectedMoreKey getExpectedEmojiMoreKey() {
        return new ExpectedMoreKey(KeyboardIconsSet.NAME_EMOJI_ACTION_KEY);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mExpectedNavigateNextMoreKey = getExpectedNavigateNextMoreKey();
        mExpectedNavigatePreviousMoreKey =  getExpectedNavigatePreviousMoreKey();
        mExpectedEmojiMoreKey = getExpectedEmojiMoreKey();
    }

    /**
     * This class represents an expected more key.
     */
    protected static class ExpectedMoreKey {
        public static final int NO_LABEL = 0;
        public static final ExpectedMoreKey[] EMPTY_MORE_KEYS = new ExpectedMoreKey[0];

        public final int mLabelResId;
        public final int mIconId;

        public ExpectedMoreKey(final String iconName) {
            mLabelResId = NO_LABEL;
            mIconId = KeyboardIconsSet.getIconId(iconName);
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
        final KeyboardLayoutSet layoutSet = createKeyboardLayoutSet(subtype, editorInfo);
        final Keyboard keyboard = layoutSet.getKeyboard(elementId);

        final Key actualKey = keyboard.getKey(code);
        final MoreKeySpec[] actualMoreKeys = actualKey.getMoreKeys();
        final String tag = actualKey.toString() + " moreKeys=" + Arrays.toString(actualMoreKeys);
        if (expectedMoreKeys.length == 0) {
            assertEquals(tag, null, actualMoreKeys);
            return;
        }
        if (expectedMoreKeys.length == 1) {
            assertEquals(tag + " fixedOrder", false, actualKey.isMoreKeysFixedOrder());
            assertEquals(tag + " fixedColumn", false, actualKey.isMoreKeysFixedColumn());
        } else {
            assertEquals(tag + " fixedOrder", true, actualKey.isMoreKeysFixedOrder());
            assertEquals(tag + " fixedColumn", true, actualKey.isMoreKeysFixedColumn());
            // TODO: Can't handle multiple rows of more keys.
            assertEquals(tag + " column",
                    expectedMoreKeys.length, actualKey.getMoreKeysColumnNumber());
        }
        assertNotNull(tag + " moreKeys", actualMoreKeys);
        assertEquals(tag, expectedMoreKeys.length, actualMoreKeys.length);
        for (int index = 0; index < actualMoreKeys.length; index++) {
            final int expectedLabelResId = expectedMoreKeys[index].mLabelResId;
            if (expectedLabelResId == ExpectedMoreKey.NO_LABEL) {
                assertEquals(tag + " label " + index, null, actualMoreKeys[index].mLabel);
            } else {
                final CharSequence expectedLabel = getContext().getText(expectedLabelResId);
                assertEquals(tag + " label " + index, expectedLabel, actualMoreKeys[index].mLabel);
            }
            final int expectedIconId = expectedMoreKeys[index].mIconId;
            assertEquals(tag + " icon " + index, expectedIconId, actualMoreKeys[index].mIconId);
        }
    }

    private void doTestNavigationMoreKeysOf(final int code, final InputMethodSubtype subtype,
            final int elementId, final int inputType) {
        // No navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_NULL,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // With next navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_FLAG_NAVIGATE_NEXT,
                mExpectedNavigateNextMoreKey);
        // With previous navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                mExpectedNavigatePreviousMoreKey);
        // With next and previous naviagte flags.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_FLAG_NAVIGATE_NEXT | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                mExpectedNavigatePreviousMoreKey, mExpectedNavigateNextMoreKey);
        // Action next.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // Action next with next navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NAVIGATE_NEXT,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // Action next with previous navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                mExpectedNavigatePreviousMoreKey);
        // Action next with next and previous navigate flags.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NAVIGATE_NEXT
                        | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                mExpectedNavigatePreviousMoreKey);
        // Action previous.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // Action previous with next navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS | EditorInfo.IME_FLAG_NAVIGATE_NEXT,
                mExpectedNavigateNextMoreKey);
        // Action previous with previous navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // Action previous with next and previous navigate flags.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS | EditorInfo.IME_FLAG_NAVIGATE_NEXT
                        | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                mExpectedNavigateNextMoreKey);
    }

    private void doTestNavigationWithEmojiMoreKeysOf(final int code,
            final InputMethodSubtype subtype, final int elementId, final int inputType) {
        // No navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_NULL,
                mExpectedEmojiMoreKey);
        // With next navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_FLAG_NAVIGATE_NEXT,
                mExpectedEmojiMoreKey, mExpectedNavigateNextMoreKey);
        // With previous navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                mExpectedEmojiMoreKey, mExpectedNavigatePreviousMoreKey);
        // With next and previous naviagte flags.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_FLAG_NAVIGATE_NEXT | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                mExpectedEmojiMoreKey, mExpectedNavigatePreviousMoreKey,
                mExpectedNavigateNextMoreKey);
        // Action next.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT,
                mExpectedEmojiMoreKey);
        // Action next with next navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NAVIGATE_NEXT,
                mExpectedEmojiMoreKey);
        // Action next with previous navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                mExpectedEmojiMoreKey, mExpectedNavigatePreviousMoreKey);
        // Action next with next and previous navigate flags.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NAVIGATE_NEXT
                        | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                mExpectedEmojiMoreKey, mExpectedNavigatePreviousMoreKey);
        // Action previous.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS,
                mExpectedEmojiMoreKey);
        // Action previous with next navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS | EditorInfo.IME_FLAG_NAVIGATE_NEXT,
                mExpectedEmojiMoreKey, mExpectedNavigateNextMoreKey);
        // Action previous with previous navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                mExpectedEmojiMoreKey);
        // Action previous with next and previous navigate flags.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS | EditorInfo.IME_FLAG_NAVIGATE_NEXT
                        | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                mExpectedEmojiMoreKey, mExpectedNavigateNextMoreKey);
    }

    private void doTestNoNavigationMoreKeysOf(final int code, final InputMethodSubtype subtype,
            final int elementId, final int inputType) {
        // No navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_NULL,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // With next navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_FLAG_NAVIGATE_NEXT,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // With previous navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // With next and previous naviagte flags.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_FLAG_NAVIGATE_NEXT | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // Action next.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // Action next with next navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NAVIGATE_NEXT,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // Action next with previous navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // Action next with next and previous navigate flags.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NAVIGATE_NEXT
                        | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // Action previous.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // Action previous with next navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS | EditorInfo.IME_FLAG_NAVIGATE_NEXT,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // Action previous with previous navigate flag.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
        // Action previous with next and previous navigate flags.
        doTestMoreKeysOf(code, subtype, elementId, inputType,
                EditorInfo.IME_ACTION_PREVIOUS | EditorInfo.IME_FLAG_NAVIGATE_NEXT
                        | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS,
                ExpectedMoreKey.EMPTY_MORE_KEYS);
    }

    public void testMoreKeysOfEnterKey() {
        final RichInputMethodManager richImm = RichInputMethodManager.getInstance();
        final InputMethodSubtype subtype = richImm.findSubtypeByLocaleAndKeyboardLayoutSet(
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
        if (isPhone()) {
            // The enter key has an Emoji key as one of more keys.
            doTestNavigationWithEmojiMoreKeysOf(Constants.CODE_ENTER, subtype,
                    KeyboardId.ELEMENT_ALPHABET,
                    InputType.TYPE_CLASS_TEXT);
        } else {
            // Tablet has a dedicated Emoji key, so the Enter key has no Emoji more key.
            doTestNavigationMoreKeysOf(Constants.CODE_ENTER, subtype,
                    KeyboardId.ELEMENT_ALPHABET,
                    InputType.TYPE_CLASS_TEXT);
        }
        // Short message field.
        if (isPhone()) {
            // Enter key is switched to Emoji key on a short message field.
            // Emoji key has no navigation more keys.
            doTestNoNavigationMoreKeysOf(Constants.CODE_EMOJI, subtype,
                    KeyboardId.ELEMENT_ALPHABET,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
        } else {
            doTestNavigationMoreKeysOf(Constants.CODE_ENTER, subtype,
                    KeyboardId.ELEMENT_ALPHABET,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
        }
    }
}
