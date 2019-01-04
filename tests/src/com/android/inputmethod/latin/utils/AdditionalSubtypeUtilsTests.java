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

package com.android.inputmethod.latin.utils;

import static com.android.inputmethod.latin.common.Constants.Subtype.ExtraValue.ASCII_CAPABLE;
import static com.android.inputmethod.latin.common.Constants.Subtype.ExtraValue.EMOJI_CAPABLE;
import static com.android.inputmethod.latin.common.Constants.Subtype.ExtraValue.IS_ADDITIONAL_SUBTYPE;
import static com.android.inputmethod.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET;
import static com.android.inputmethod.latin.common.Constants.Subtype.ExtraValue.UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME;
import static com.android.inputmethod.latin.common.Constants.Subtype.KEYBOARD_MODE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.view.inputmethod.InputMethodSubtype;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.compat.InputMethodSubtypeCompatUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AdditionalSubtypeUtilsTests {

    /**
     * Predictable subtype ID for en_US dvorak layout. This is actually a hash code calculated as
     * follows.
     * <code>
     * final boolean isAuxiliary = false;
     * final boolean overrideImplicitlyEnabledSubtype = false;
     * final int SUBTYPE_ID_EN_US_DVORAK = Arrays.hashCode(new Object[] {
     *         "en_US",
     *         "keyboard",
     *         "KeyboardLayoutSet=dvorak"
     *                 + ",AsciiCapable"
     *                 + ",UntranslatableReplacementStringInSubtypeName=Dvorak"
     *                 + ",EmojiCapable"
     *                 + ",isAdditionalSubtype",
     *         isAuxiliary,
     *         overrideImplicitlyEnabledSubtype });
     * </code>
     */
    private static int SUBTYPE_ID_EN_US_DVORAK = 0xb3c0cc56;
    private static String EXTRA_VALUE_EN_US_DVORAK_ICS =
            "KeyboardLayoutSet=dvorak" +
            ",AsciiCapable" +
            ",isAdditionalSubtype";
    private static String EXTRA_VALUE_EN_US_DVORAK_JELLY_BEAN =
            "KeyboardLayoutSet=dvorak" +
            ",AsciiCapable" +
            ",UntranslatableReplacementStringInSubtypeName=Dvorak" +
            ",isAdditionalSubtype";
    private static String EXTRA_VALUE_EN_US_DVORAK_KITKAT =
            "KeyboardLayoutSet=dvorak" +
            ",AsciiCapable" +
            ",UntranslatableReplacementStringInSubtypeName=Dvorak" +
            ",EmojiCapable" +
            ",isAdditionalSubtype";

    /**
     * Predictable subtype ID for azerty layout. This is actually a hash code calculated as follows.
     * <code>
     * final boolean isAuxiliary = false;
     * final boolean overrideImplicitlyEnabledSubtype = false;
     * final int SUBTYPE_ID_ZZ_AZERTY = Arrays.hashCode(new Object[] {
     *         "zz",
     *         "keyboard",
     *         "KeyboardLayoutSet=azerty"
     *                 + ",AsciiCapable"
     *                 + ",EmojiCapable"
     *                 + ",isAdditionalSubtype",
     *         isAuxiliary,
     *         overrideImplicitlyEnabledSubtype });
     * </code>
     */
    private static int SUBTYPE_ID_ZZ_AZERTY = 0x5b6be697;
    private static String EXTRA_VALUE_ZZ_AZERTY_ICS =
            "KeyboardLayoutSet=azerty" +
            ",AsciiCapable" +
            ",isAdditionalSubtype";
    private static String EXTRA_VALUE_ZZ_AZERTY_KITKAT =
            "KeyboardLayoutSet=azerty" +
            ",AsciiCapable" +
            ",EmojiCapable" +
            ",isAdditionalSubtype";

    @Before
    public void setUp() throws Exception {
        final Context context = InstrumentationRegistry.getTargetContext();
        SubtypeLocaleUtils.init(context);
    }

    private static void assertEnUsDvorak(InputMethodSubtype subtype) {
        assertEquals("en_US", subtype.getLocale());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            assertEquals(EXTRA_VALUE_EN_US_DVORAK_KITKAT, subtype.getExtraValue());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            assertEquals(EXTRA_VALUE_EN_US_DVORAK_JELLY_BEAN, subtype.getExtraValue());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            assertEquals(EXTRA_VALUE_EN_US_DVORAK_ICS, subtype.getExtraValue());
        }
        assertTrue(subtype.containsExtraValueKey(ASCII_CAPABLE));
        assertTrue(InputMethodSubtypeCompatUtils.isAsciiCapable(subtype));
        // TODO: Enable following test
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        //    assertTrue(InputMethodSubtypeCompatUtils.isAsciiCapableWithAPI(subtype));
        // }
        assertTrue(subtype.containsExtraValueKey(EMOJI_CAPABLE));
        assertTrue(subtype.containsExtraValueKey(IS_ADDITIONAL_SUBTYPE));
        assertEquals("dvorak", subtype.getExtraValueOf(KEYBOARD_LAYOUT_SET));
        assertEquals("Dvorak", subtype.getExtraValueOf(UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME));
        assertEquals(KEYBOARD_MODE, subtype.getMode());
        assertEquals(SUBTYPE_ID_EN_US_DVORAK, subtype.hashCode());
    }

    private static void assertAzerty(InputMethodSubtype subtype) {
        assertEquals("zz", subtype.getLocale());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            assertEquals(EXTRA_VALUE_ZZ_AZERTY_KITKAT, subtype.getExtraValue());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            assertEquals(EXTRA_VALUE_ZZ_AZERTY_ICS, subtype.getExtraValue());
        }
        assertTrue(subtype.containsExtraValueKey(ASCII_CAPABLE));
        assertTrue(InputMethodSubtypeCompatUtils.isAsciiCapable(subtype));
        // TODO: Enable following test
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        //    assertTrue(InputMethodSubtypeCompatUtils.isAsciiCapableWithAPI(subtype));
        // }
        assertTrue(subtype.containsExtraValueKey(EMOJI_CAPABLE));
        assertTrue(subtype.containsExtraValueKey(IS_ADDITIONAL_SUBTYPE));
        assertEquals("azerty", subtype.getExtraValueOf(KEYBOARD_LAYOUT_SET));
        assertFalse(subtype.containsExtraValueKey(UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME));
        assertEquals(KEYBOARD_MODE, subtype.getMode());
        assertEquals(SUBTYPE_ID_ZZ_AZERTY, subtype.hashCode());
    }

    @Test
    public void testRestorable() {
        final InputMethodSubtype EN_US_DVORAK =
                AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                        Locale.US.toString(), "dvorak");
        final InputMethodSubtype ZZ_AZERTY =
                AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                        SubtypeLocaleUtils.NO_LANGUAGE, "azerty");
        assertEnUsDvorak(EN_US_DVORAK);
        assertAzerty(ZZ_AZERTY);

        // Make sure the subtype can be stored and restored in a deterministic manner.
        final InputMethodSubtype[] subtypes = { EN_US_DVORAK, ZZ_AZERTY };
        final String prefSubtype = AdditionalSubtypeUtils.createPrefSubtypes(subtypes);
        final InputMethodSubtype[] restoredSubtypes =
                AdditionalSubtypeUtils.createAdditionalSubtypesArray(prefSubtype);
        assertEquals(2, restoredSubtypes.length);
        final InputMethodSubtype restored_EN_US_DVORAK = restoredSubtypes[0];
        final InputMethodSubtype restored_ZZ_AZERTY = restoredSubtypes[1];

        assertEnUsDvorak(restored_EN_US_DVORAK);
        assertAzerty(restored_ZZ_AZERTY);
    }
}
