/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.keyboard;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.DisplayMetrics;
import android.util.Xml;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.keyboard.internal.KeyboardBuilder;
import com.android.inputmethod.keyboard.internal.KeyboardBuilder.IllegalEndTag;
import com.android.inputmethod.keyboard.internal.KeyboardBuilder.IllegalStartTag;
import com.android.inputmethod.keyboard.internal.KeyboardBuilder.ParseException;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LocaleUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SettingsValues;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.Utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

/**
 * This class has a set of {@link KeyboardId}s. Each of them represents a different keyboard
 * specific to a keyboard state, such as alphabet, symbols, and so on.  Layouts in the same
 * {@link KeyboardSet} are related to each other.
 * A {@link KeyboardSet} needs to be created for each {@link android.view.inputmethod.EditorInfo}.
 */
public class KeyboardSet {
    private static final String TAG_KEYBOARD_SET = "KeyboardSet";
    private static final String TAG_ELEMENT = "Element";

    // TODO: Make these KeyboardId private.
    public final KeyboardId mAlphabetId;
    public final KeyboardId mSymbolsId;
    public final KeyboardId mSymbolsShiftedId;

    KeyboardSet(Builder builder) {
        mAlphabetId = builder.getKeyboardId(false, false);
        mSymbolsId = builder.getKeyboardId(true, false);
        mSymbolsShiftedId = builder.getKeyboardId(true, true);
    }

    public static class Builder {
        private final Resources mResources;
        private final EditorInfo mEditorInfo;

        private final HashMap<Integer, Integer> mElementKeyboards =
                new HashMap<Integer, Integer>();

        private final int mMode;
        private final boolean mVoiceKeyEnabled;
        private final boolean mNoSettingsKey;
        private final boolean mHasSettingsKey;
        private final int mF2KeyMode;
        private final boolean mVoiceKeyOnMain;
        private final Locale mLocale;
        private final Configuration mConf;
        private final DisplayMetrics mMetrics;

        public Builder(Context context, EditorInfo editorInfo, SettingsValues settingsValues) {
            mResources = context.getResources();
            mEditorInfo = editorInfo;
            final SubtypeSwitcher subtypeSwitcher = SubtypeSwitcher.getInstance();
            final String packageName = context.getPackageName();

            mMode = Utils.getKeyboardMode(mEditorInfo);
            final boolean settingsKeyEnabled = settingsValues.isSettingsKeyEnabled();
            @SuppressWarnings("deprecation")
            final boolean noMicrophone = Utils.inPrivateImeOptions(
                    packageName, LatinIME.IME_OPTION_NO_MICROPHONE, editorInfo)
                    || Utils.inPrivateImeOptions(
                            null, LatinIME.IME_OPTION_NO_MICROPHONE_COMPAT, editorInfo);
            mVoiceKeyEnabled = settingsValues.isVoiceKeyEnabled(editorInfo) && !noMicrophone;
            mVoiceKeyOnMain = settingsValues.isVoiceKeyOnMain();
            mNoSettingsKey = Utils.inPrivateImeOptions(
                    packageName, LatinIME.IME_OPTION_NO_SETTINGS_KEY, editorInfo);
            mHasSettingsKey = settingsKeyEnabled && !mNoSettingsKey;
            mF2KeyMode = getF2KeyMode(settingsKeyEnabled, mNoSettingsKey);
            final boolean forceAscii = Utils.inPrivateImeOptions(
                    packageName, LatinIME.IME_OPTION_FORCE_ASCII, editorInfo);
            final boolean asciiCapable = subtypeSwitcher.currentSubtypeContainsExtraValueKey(
                    LatinIME.SUBTYPE_EXTRA_VALUE_ASCII_CAPABLE);
            mLocale = (forceAscii && !asciiCapable) ? Locale.US : subtypeSwitcher.getInputLocale();
            mConf = mResources.getConfiguration();
            mMetrics = mResources.getDisplayMetrics();
        }

        public KeyboardSet build() {
            final Locale savedLocale = LocaleUtils.setSystemLocale(mResources, mLocale);
            try {
                parseKeyboardSet(mResources, R.xml.keyboard_set);
            } catch (Exception e) {
                //
            } finally {
                LocaleUtils.setSystemLocale(mResources, savedLocale);
            }
            return new KeyboardSet(this);
        }

        KeyboardId getKeyboardId(boolean isSymbols, boolean isShift) {
            final int elementState = getElementState(mMode, isSymbols, isShift);
            final int xmlId = mElementKeyboards.get(elementState);
            final boolean hasShortcutKey = mVoiceKeyEnabled && (isSymbols != mVoiceKeyOnMain);
            return new KeyboardId(xmlId, elementState, mLocale, mConf.orientation,
                    mMetrics.widthPixels, mMode, mEditorInfo, mHasSettingsKey, mF2KeyMode,
                    mNoSettingsKey, mVoiceKeyEnabled, hasShortcutKey);
        }

        private static int getElementState(int mode, boolean isSymbols, boolean isShift) {
            switch (mode) {
            case KeyboardId.MODE_PHONE:
                return (isSymbols && isShift)
                        ? KeyboardId.ELEMENT_PHONE_SHIFT : KeyboardId.ELEMENT_PHONE;
            case KeyboardId.MODE_NUMBER:
                return KeyboardId.ELEMENT_NUMBER;
            default:
                if (isSymbols) {
                    return isShift ? KeyboardId.ELEMENT_SYMBOLS_SHIFT : KeyboardId.ELEMENT_SYMBOLS;
                }
                return KeyboardId.ELEMENT_ALPHABET;
            }
        }

        // TODO: Move to KeyboardId.
        private static int getF2KeyMode(boolean settingsKeyEnabled, boolean noSettingsKey) {
            if (noSettingsKey) {
                // Never shows the Settings key
                return KeyboardId.F2KEY_MODE_SHORTCUT_IME;
            }

            if (settingsKeyEnabled) {
                return KeyboardId.F2KEY_MODE_SETTINGS;
            } else {
                // It should be alright to fall back to the Settings key on 7-inch layouts
                // even when the Settings key is not explicitly enabled.
                return KeyboardId.F2KEY_MODE_SHORTCUT_IME_OR_SETTINGS;
            }
        }

        private void parseKeyboardSet(Resources res, int resId) throws XmlPullParserException,
                IOException {
            final XmlResourceParser parser = res.getXml(resId);
            try {
                int event;
                while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        final String tag = parser.getName();
                        if (TAG_KEYBOARD_SET.equals(tag)) {
                            parseKeyboardSetContent(parser);
                        } else {
                            throw new IllegalStartTag(parser, TAG_KEYBOARD_SET);
                        }
                    }
                }
            } finally {
                parser.close();
            }
        }

        private void parseKeyboardSetContent(XmlPullParser parser) throws XmlPullParserException,
                IOException {
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    final String tag = parser.getName();
                    if (TAG_ELEMENT.equals(tag)) {
                        parseKeyboardSetElement(parser);
                    } else {
                        throw new IllegalStartTag(parser, TAG_KEYBOARD_SET);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    final String tag = parser.getName();
                    if (TAG_KEYBOARD_SET.equals(tag)) {
                        break;
                    } else {
                        throw new IllegalEndTag(parser, TAG_KEYBOARD_SET);
                    }
                }
            }
        }

        private void parseKeyboardSetElement(XmlPullParser parser) throws XmlPullParserException,
                IOException {
            final TypedArray a = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.KeyboardSet_Element);
            try {
                if (!a.hasValue(R.styleable.KeyboardSet_Element_elementName)) {
                    throw new ParseException(
                            "No elementName attribute in <" + TAG_ELEMENT + "/>", parser);
                }
                if (!a.hasValue(R.styleable.KeyboardSet_Element_elementKeyboard)) {
                    throw new ParseException(
                            "No elementKeyboard attribute in <" + TAG_ELEMENT + "/>", parser);
                }
                KeyboardBuilder.checkEndTag(TAG_ELEMENT, parser);

                final int elementName = a.getInt(
                        R.styleable.KeyboardSet_Element_elementName, 0);
                final int elementKeyboard = a.getResourceId(
                        R.styleable.KeyboardSet_Element_elementKeyboard, 0);
                mElementKeyboards.put(elementName, elementKeyboard);
            } finally {
                a.recycle();
            }
        }
    }

    public static String parseKeyboardLocale(Resources res, int resId)
            throws XmlPullParserException, IOException {
        final XmlPullParser parser = res.getXml(resId);
        if (parser == null) return "";
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                final String tag = parser.getName();
                if (TAG_KEYBOARD_SET.equals(tag)) {
                    final TypedArray keyboardSetAttr = res.obtainAttributes(
                            Xml.asAttributeSet(parser), R.styleable.KeyboardSet);
                    final String locale = keyboardSetAttr.getString(
                            R.styleable.KeyboardSet_keyboardLocale);
                    keyboardSetAttr.recycle();
                    return locale;
                } else {
                    throw new IllegalStartTag(parser, TAG_KEYBOARD_SET);
                }
            }
        }
        return "";
    }
}
