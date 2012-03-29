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
import android.text.InputType;
import android.util.Log;
import android.util.Xml;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.EditorInfoCompatUtils;
import com.android.inputmethod.compat.InputTypeCompatUtils;
import com.android.inputmethod.keyboard.KeyboardSet.Params.ElementParams;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.LocaleUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StringUtils;
import com.android.inputmethod.latin.XmlParseUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Locale;

/**
 * This class represents a set of keyboards. Each of them represents a different keyboard
 * specific to a keyboard state, such as alphabet, symbols, and so on.  Layouts in the same
 * {@link KeyboardSet} are related to each other.
 * A {@link KeyboardSet} needs to be created for each {@link android.view.inputmethod.EditorInfo}.
 */
public class KeyboardSet {
    private static final String TAG = KeyboardSet.class.getSimpleName();
    private static final boolean DEBUG_CACHE = LatinImeLogger.sDBG;

    private static final String TAG_KEYBOARD_SET = "KeyboardSet";
    private static final String TAG_ELEMENT = "Element";

    private final Context mContext;
    private final Params mParams;

    private static final HashMap<KeyboardId, SoftReference<Keyboard>> sKeyboardCache =
            new HashMap<KeyboardId, SoftReference<Keyboard>>();
    private static final KeysCache sKeysCache = new KeysCache();

    public static class KeyboardSetException extends RuntimeException {
        public final KeyboardId mKeyboardId;
        public KeyboardSetException(Throwable cause, KeyboardId keyboardId) {
            super(cause);
            mKeyboardId = keyboardId;
        }
    }

    public static class KeysCache {
        private final HashMap<Key, Key> mMap;

        public KeysCache() {
            mMap = new HashMap<Key, Key>();
        }

        public void clear() {
            mMap.clear();
        }

        public Key get(Key key) {
            final Key existingKey = mMap.get(key);
            if (existingKey != null) {
                // Reuse the existing element that equals to "key" without adding "key" to the map.
                return existingKey;
            }
            mMap.put(key, key);
            return key;
        }
    }

    static class Params {
        int mMode;
        EditorInfo mEditorInfo;
        boolean mTouchPositionCorrectionEnabled;
        boolean mDisableShortcutKey;
        boolean mVoiceKeyEnabled;
        boolean mVoiceKeyOnMain;
        boolean mNoSettingsKey;
        boolean mLanguageSwitchKeyEnabled;
        Locale mLocale;
        int mOrientation;
        int mWidth;
        // KeyboardSet element id to element's parameters map.
        final HashMap<Integer, ElementParams> mKeyboardSetElementIdToParamsMap =
                new HashMap<Integer, ElementParams>();

        static class ElementParams {
            int mKeyboardXmlId;
            boolean mProximityCharsCorrectionEnabled;
        }
    }

    public static void clearKeyboardCache() {
        sKeyboardCache.clear();
        sKeysCache.clear();
    }

    private KeyboardSet(Context context, Params params) {
        mContext = context;
        mParams = params;
    }

    public Keyboard getKeyboard(int baseKeyboardSetElementId) {
        final int keyboardSetElementId;
        switch (mParams.mMode) {
        case KeyboardId.MODE_PHONE:
            if (baseKeyboardSetElementId == KeyboardId.ELEMENT_SYMBOLS) {
                keyboardSetElementId = KeyboardId.ELEMENT_PHONE_SYMBOLS;
            } else {
                keyboardSetElementId = KeyboardId.ELEMENT_PHONE;
            }
            break;
        case KeyboardId.MODE_NUMBER:
        case KeyboardId.MODE_DATE:
        case KeyboardId.MODE_TIME:
        case KeyboardId.MODE_DATETIME:
            keyboardSetElementId = KeyboardId.ELEMENT_NUMBER;
            break;
        default:
            keyboardSetElementId = baseKeyboardSetElementId;
            break;
        }

        ElementParams elementParams = mParams.mKeyboardSetElementIdToParamsMap.get(
                keyboardSetElementId);
        if (elementParams == null) {
            elementParams = mParams.mKeyboardSetElementIdToParamsMap.get(
                    KeyboardId.ELEMENT_ALPHABET);
        }
        final KeyboardId id = getKeyboardId(keyboardSetElementId);
        try {
            return getKeyboard(mContext, elementParams, id);
        } catch (RuntimeException e) {
            throw new KeyboardSetException(e, id);
        }
    }

    private Keyboard getKeyboard(Context context, ElementParams elementParams, KeyboardId id) {
        final Resources res = context.getResources();
        final SoftReference<Keyboard> ref = sKeyboardCache.get(id);
        Keyboard keyboard = (ref == null) ? null : ref.get();
        if (keyboard == null) {
            final Locale savedLocale = LocaleUtils.setSystemLocale(res, id.mLocale);
            try {
                final Keyboard.Builder<Keyboard.Params> builder =
                        new Keyboard.Builder<Keyboard.Params>(context, new Keyboard.Params());
                if (id.isAlphabetKeyboard()) {
                    builder.setAutoGenerate(sKeysCache);
                }
                builder.load(elementParams.mKeyboardXmlId, id);
                builder.setTouchPositionCorrectionEnabled(mParams.mTouchPositionCorrectionEnabled);
                builder.setProximityCharsCorrectionEnabled(
                        elementParams.mProximityCharsCorrectionEnabled);
                keyboard = builder.build();
            } finally {
                LocaleUtils.setSystemLocale(res, savedLocale);
            }
            sKeyboardCache.put(id, new SoftReference<Keyboard>(keyboard));

            if (DEBUG_CACHE) {
                Log.d(TAG, "keyboard cache size=" + sKeyboardCache.size() + ": "
                        + ((ref == null) ? "LOAD" : "GCed") + " id=" + id);
            }
        } else if (DEBUG_CACHE) {
            Log.d(TAG, "keyboard cache size=" + sKeyboardCache.size() + ": HIT  id=" + id);
        }

        return keyboard;
    }

    // Note: The keyboard for each locale, shift state, and mode are represented as KeyboardSet
    // element id that is a key in keyboard_set.xml.  Also that file specifies which XML layout
    // should be used for each keyboard.  The KeyboardId is an internal key for Keyboard object.
    private KeyboardId getKeyboardId(int keyboardSetElementId) {
        final Params params = mParams;
        final boolean isSymbols = (keyboardSetElementId == KeyboardId.ELEMENT_SYMBOLS
                || keyboardSetElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED);
        final boolean voiceKeyEnabled = params.mVoiceKeyEnabled && !params.mDisableShortcutKey;
        final boolean hasShortcutKey = voiceKeyEnabled && (isSymbols != params.mVoiceKeyOnMain);
        return new KeyboardId(keyboardSetElementId, params.mLocale, params.mOrientation,
                params.mWidth, params.mMode, params.mEditorInfo, params.mNoSettingsKey,
                voiceKeyEnabled, hasShortcutKey, params.mLanguageSwitchKeyEnabled);
    }

    public static class Builder {
        private final Context mContext;
        private final String mPackageName;
        private final Resources mResources;
        private final EditorInfo mEditorInfo;

        private final Params mParams = new Params();

        private static final EditorInfo EMPTY_EDITOR_INFO = new EditorInfo();

        public Builder(Context context, EditorInfo editorInfo) {
            mContext = context;
            mPackageName = context.getPackageName();
            mResources = context.getResources();
            mEditorInfo = editorInfo;
            final Params params = mParams;

            params.mMode = getKeyboardMode(editorInfo);
            params.mEditorInfo = (editorInfo != null) ? editorInfo : EMPTY_EDITOR_INFO;
            params.mNoSettingsKey = StringUtils.inPrivateImeOptions(
                    mPackageName, LatinIME.IME_OPTION_NO_SETTINGS_KEY, mEditorInfo);
        }

        public Builder setScreenGeometry(int orientation, int widthPixels) {
            mParams.mOrientation = orientation;
            mParams.mWidth = widthPixels;
            return this;
        }

        // TODO: Use InputMethodSubtype object as argument.
        public Builder setSubtype(Locale inputLocale, boolean asciiCapable) {
            final boolean deprecatedForceAscii = StringUtils.inPrivateImeOptions(
                    mPackageName, LatinIME.IME_OPTION_FORCE_ASCII, mEditorInfo);
            final boolean forceAscii = EditorInfoCompatUtils.hasFlagForceAscii(
                    mParams.mEditorInfo.imeOptions)
                    || deprecatedForceAscii;
            mParams.mLocale = (forceAscii && !asciiCapable) ? Locale.US : inputLocale;
            return this;
        }

        public Builder setOptions(boolean voiceKeyEnabled, boolean voiceKeyOnMain,
                boolean languageSwitchKeyEnabled) {
            @SuppressWarnings("deprecation")
            final boolean deprecatedNoMicrophone = StringUtils.inPrivateImeOptions(
                    null, LatinIME.IME_OPTION_NO_MICROPHONE_COMPAT, mEditorInfo);
            final boolean noMicrophone = StringUtils.inPrivateImeOptions(
                    mPackageName, LatinIME.IME_OPTION_NO_MICROPHONE, mEditorInfo)
                    || deprecatedNoMicrophone;
            mParams.mVoiceKeyEnabled = voiceKeyEnabled && !noMicrophone;
            mParams.mVoiceKeyOnMain = voiceKeyOnMain;
            mParams.mLanguageSwitchKeyEnabled = languageSwitchKeyEnabled;
            return this;
        }

        public void setTouchPositionCorrectionEnabled(boolean enabled) {
            mParams.mTouchPositionCorrectionEnabled = enabled;
        }

        public KeyboardSet build() {
            if (mParams.mOrientation == Configuration.ORIENTATION_UNDEFINED)
                throw new RuntimeException("Screen geometry is not specified");
            if (mParams.mLocale == null)
                throw new RuntimeException("KeyboardSet subtype is not specified");

            final Locale savedLocale = LocaleUtils.setSystemLocale(mResources, mParams.mLocale);
            try {
                parseKeyboardSet(mResources, R.xml.keyboard_set);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage() + " in "
                        + mResources.getResourceName(R.xml.keyboard_set)
                        + " of locale " + mParams.mLocale);
            } finally {
                LocaleUtils.setSystemLocale(mResources, savedLocale);
            }
            return new KeyboardSet(mContext, mParams);
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
                            final TypedArray a = mResources.obtainAttributes(
                                    Xml.asAttributeSet(parser), R.styleable.KeyboardSet);
                            mParams.mDisableShortcutKey = a.getBoolean(
                                    R.styleable.KeyboardSet_disableShortcutKey, false);
                            a.recycle();
                            parseKeyboardSetContent(parser);
                        } else {
                            throw new XmlParseUtils.IllegalStartTag(parser, TAG_KEYBOARD_SET);
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
                        throw new XmlParseUtils.IllegalStartTag(parser, TAG_KEYBOARD_SET);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    final String tag = parser.getName();
                    if (TAG_KEYBOARD_SET.equals(tag)) {
                        break;
                    } else {
                        throw new XmlParseUtils.IllegalEndTag(parser, TAG_KEYBOARD_SET);
                    }
                }
            }
        }

        private void parseKeyboardSetElement(XmlPullParser parser) throws XmlPullParserException,
                IOException {
            final TypedArray a = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.KeyboardSet_Element);
            try {
                XmlParseUtils.checkAttributeExists(a,
                        R.styleable.KeyboardSet_Element_elementName, "elementName",
                        TAG_ELEMENT, parser);
                XmlParseUtils.checkAttributeExists(a,
                        R.styleable.KeyboardSet_Element_elementKeyboard, "elementKeyboard",
                        TAG_ELEMENT, parser);
                XmlParseUtils.checkEndTag(TAG_ELEMENT, parser);

                final ElementParams elementParams = new ElementParams();
                final int elementName = a.getInt(
                        R.styleable.KeyboardSet_Element_elementName, 0);
                elementParams.mKeyboardXmlId = a.getResourceId(
                        R.styleable.KeyboardSet_Element_elementKeyboard, 0);
                elementParams.mProximityCharsCorrectionEnabled = a.getBoolean(
                        R.styleable.KeyboardSet_Element_enableProximityCharsCorrection, false);
                mParams.mKeyboardSetElementIdToParamsMap.put(elementName, elementParams);
            } finally {
                a.recycle();
            }
        }

        private static int getKeyboardMode(EditorInfo editorInfo) {
            if (editorInfo == null)
                return KeyboardId.MODE_TEXT;

            final int inputType = editorInfo.inputType;
            final int variation = inputType & InputType.TYPE_MASK_VARIATION;

            switch (inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
                return KeyboardId.MODE_NUMBER;
            case InputType.TYPE_CLASS_DATETIME:
                switch (variation) {
                case InputType.TYPE_DATETIME_VARIATION_DATE:
                    return KeyboardId.MODE_DATE;
                case InputType.TYPE_DATETIME_VARIATION_TIME:
                    return KeyboardId.MODE_TIME;
                default: // InputType.TYPE_DATETIME_VARIATION_NORMAL
                    return KeyboardId.MODE_DATETIME;
                }
            case InputType.TYPE_CLASS_PHONE:
                return KeyboardId.MODE_PHONE;
            case InputType.TYPE_CLASS_TEXT:
                if (InputTypeCompatUtils.isEmailVariation(variation)) {
                    return KeyboardId.MODE_EMAIL;
                } else if (variation == InputType.TYPE_TEXT_VARIATION_URI) {
                    return KeyboardId.MODE_URL;
                } else if (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                    return KeyboardId.MODE_IM;
                } else if (variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                    return KeyboardId.MODE_TEXT;
                } else {
                    return KeyboardId.MODE_TEXT;
                }
            default:
                return KeyboardId.MODE_TEXT;
            }
        }
    }
}
