/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static com.android.inputmethod.latin.Constants.ImeOption.FORCE_ASCII;
import static com.android.inputmethod.latin.Constants.ImeOption.NO_MICROPHONE;
import static com.android.inputmethod.latin.Constants.ImeOption.NO_MICROPHONE_COMPAT;
import static com.android.inputmethod.latin.Constants.ImeOption.NO_SETTINGS_KEY;
import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.ASCII_CAPABLE;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.text.InputType;
import android.util.Log;
import android.util.SparseArray;
import android.util.Xml;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.compat.EditorInfoCompatUtils;
import com.android.inputmethod.keyboard.internal.KeyboardBuilder;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.keyboard.internal.KeysCache;
import com.android.inputmethod.latin.InputAttributes;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.InputTypeUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;
import com.android.inputmethod.latin.utils.XmlParseUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.HashMap;

/**
 * This class represents a set of keyboard layouts. Each of them represents a different keyboard
 * specific to a keyboard state, such as alphabet, symbols, and so on.  Layouts in the same
 * {@link KeyboardLayoutSet} are related to each other.
 * A {@link KeyboardLayoutSet} needs to be created for each
 * {@link android.view.inputmethod.EditorInfo}.
 */
public final class KeyboardLayoutSet {
    private static final String TAG = KeyboardLayoutSet.class.getSimpleName();
    private static final boolean DEBUG_CACHE = LatinImeLogger.sDBG;

    private static final String TAG_KEYBOARD_SET = "KeyboardLayoutSet";
    private static final String TAG_ELEMENT = "Element";

    private static final String KEYBOARD_LAYOUT_SET_RESOURCE_PREFIX = "keyboard_layout_set_";

    private final Context mContext;
    private final Params mParams;

    // How many layouts we forcibly keep in cache. This only includes ALPHABET (default) and
    // ALPHABET_AUTOMATIC_SHIFTED layouts - other layouts may stay in memory in the map of
    // soft-references, but we forcibly cache this many alphabetic/auto-shifted layouts.
    private static final int FORCIBLE_CACHE_SIZE = 4;
    // By construction of soft references, anything that is also referenced somewhere else
    // will stay in the cache. So we forcibly keep some references in an array to prevent
    // them from disappearing from sKeyboardCache.
    private static final Keyboard[] sForcibleKeyboardCache = new Keyboard[FORCIBLE_CACHE_SIZE];
    private static final HashMap<KeyboardId, SoftReference<Keyboard>> sKeyboardCache =
            CollectionUtils.newHashMap();
    private static final KeysCache sKeysCache = new KeysCache();

    @SuppressWarnings("serial")
    public static final class KeyboardLayoutSetException extends RuntimeException {
        public final KeyboardId mKeyboardId;

        public KeyboardLayoutSetException(final Throwable cause, final KeyboardId keyboardId) {
            super(cause);
            mKeyboardId = keyboardId;
        }
    }

    private static final class ElementParams {
        int mKeyboardXmlId;
        boolean mProximityCharsCorrectionEnabled;
        public ElementParams() {}
    }

    public static final class Params {
        String mKeyboardLayoutSetName;
        int mMode;
        EditorInfo mEditorInfo;
        boolean mDisableTouchPositionCorrectionDataForTest;
        boolean mVoiceKeyEnabled;
        // TODO: Remove mVoiceKeyOnMain when it's certainly confirmed that we no longer show
        // the voice input key on the symbol layout
        boolean mVoiceKeyOnMain;
        boolean mNoSettingsKey;
        boolean mLanguageSwitchKeyEnabled;
        InputMethodSubtype mSubtype;
        boolean mIsSpellChecker;
        int mKeyboardWidth;
        int mKeyboardHeight;
        // Sparse array of KeyboardLayoutSet element parameters indexed by element's id.
        final SparseArray<ElementParams> mKeyboardLayoutSetElementIdToParamsMap =
                CollectionUtils.newSparseArray();
    }

    public static void clearKeyboardCache() {
        sKeyboardCache.clear();
        sKeysCache.clear();
    }

    KeyboardLayoutSet(final Context context, final Params params) {
        mContext = context;
        mParams = params;
    }

    public Keyboard getKeyboard(final int baseKeyboardLayoutSetElementId) {
        final int keyboardLayoutSetElementId;
        switch (mParams.mMode) {
        case KeyboardId.MODE_PHONE:
            if (baseKeyboardLayoutSetElementId == KeyboardId.ELEMENT_SYMBOLS) {
                keyboardLayoutSetElementId = KeyboardId.ELEMENT_PHONE_SYMBOLS;
            } else {
                keyboardLayoutSetElementId = KeyboardId.ELEMENT_PHONE;
            }
            break;
        case KeyboardId.MODE_NUMBER:
        case KeyboardId.MODE_DATE:
        case KeyboardId.MODE_TIME:
        case KeyboardId.MODE_DATETIME:
            keyboardLayoutSetElementId = KeyboardId.ELEMENT_NUMBER;
            break;
        default:
            keyboardLayoutSetElementId = baseKeyboardLayoutSetElementId;
            break;
        }

        ElementParams elementParams = mParams.mKeyboardLayoutSetElementIdToParamsMap.get(
                keyboardLayoutSetElementId);
        if (elementParams == null) {
            elementParams = mParams.mKeyboardLayoutSetElementIdToParamsMap.get(
                    KeyboardId.ELEMENT_ALPHABET);
        }
        // Note: The keyboard for each shift state, and mode are represented as an elementName
        // attribute in a keyboard_layout_set XML file.  Also each keyboard layout XML resource is
        // specified as an elementKeyboard attribute in the file.
        // The KeyboardId is an internal key for a Keyboard object.
        final KeyboardId id = new KeyboardId(keyboardLayoutSetElementId, mParams);
        try {
            return getKeyboard(elementParams, id);
        } catch (final RuntimeException e) {
            Log.e(TAG, "Can't create keyboard: " + id, e);
            throw new KeyboardLayoutSetException(e, id);
        }
    }

    private Keyboard getKeyboard(final ElementParams elementParams, final KeyboardId id) {
        final SoftReference<Keyboard> ref = sKeyboardCache.get(id);
        final Keyboard cachedKeyboard = (ref == null) ? null : ref.get();
        if (cachedKeyboard != null) {
            if (DEBUG_CACHE) {
                Log.d(TAG, "keyboard cache size=" + sKeyboardCache.size() + ": HIT  id=" + id);
            }
            return cachedKeyboard;
        }

        final KeyboardBuilder<KeyboardParams> builder =
                new KeyboardBuilder<KeyboardParams>(mContext, new KeyboardParams());
        if (id.isAlphabetKeyboard()) {
            builder.setAutoGenerate(sKeysCache);
        }
        final int keyboardXmlId = elementParams.mKeyboardXmlId;
        builder.load(keyboardXmlId, id);
        if (mParams.mDisableTouchPositionCorrectionDataForTest) {
            builder.disableTouchPositionCorrectionDataForTest();
        }
        builder.setProximityCharsCorrectionEnabled(elementParams.mProximityCharsCorrectionEnabled);
        final Keyboard keyboard = builder.build();
        sKeyboardCache.put(id, new SoftReference<Keyboard>(keyboard));
        if ((id.mElementId == KeyboardId.ELEMENT_ALPHABET
                || id.mElementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED)
                && !mParams.mIsSpellChecker) {
            // We only forcibly cache the primary, "ALPHABET", layouts.
            for (int i = sForcibleKeyboardCache.length - 1; i >= 1; --i) {
                sForcibleKeyboardCache[i] = sForcibleKeyboardCache[i - 1];
            }
            sForcibleKeyboardCache[0] = keyboard;
            if (DEBUG_CACHE) {
                Log.d(TAG, "forcing caching of keyboard with id=" + id);
            }
        }
        if (DEBUG_CACHE) {
            Log.d(TAG, "keyboard cache size=" + sKeyboardCache.size() + ": "
                    + ((ref == null) ? "LOAD" : "GCed") + " id=" + id);
        }
        return keyboard;
    }

    public static final class Builder {
        private final Context mContext;
        private final String mPackageName;
        private final Resources mResources;

        private final Params mParams = new Params();

        private static final EditorInfo EMPTY_EDITOR_INFO = new EditorInfo();

        public Builder(final Context context, final EditorInfo editorInfo) {
            mContext = context;
            mPackageName = context.getPackageName();
            mResources = context.getResources();
            final Params params = mParams;

            params.mMode = getKeyboardMode(editorInfo);
            params.mEditorInfo = (editorInfo != null) ? editorInfo : EMPTY_EDITOR_INFO;
            params.mNoSettingsKey = InputAttributes.inPrivateImeOptions(
                    mPackageName, NO_SETTINGS_KEY, params.mEditorInfo);
        }

        public Builder setKeyboardGeometry(final int keyboardWidth, final int keyboardHeight) {
            mParams.mKeyboardWidth = keyboardWidth;
            mParams.mKeyboardHeight = keyboardHeight;
            return this;
        }

        public Builder setSubtype(final InputMethodSubtype subtype) {
            final boolean asciiCapable = subtype.containsExtraValueKey(ASCII_CAPABLE);
            @SuppressWarnings("deprecation")
            final boolean deprecatedForceAscii = InputAttributes.inPrivateImeOptions(
                    mPackageName, FORCE_ASCII, mParams.mEditorInfo);
            final boolean forceAscii = EditorInfoCompatUtils.hasFlagForceAscii(
                    mParams.mEditorInfo.imeOptions)
                    || deprecatedForceAscii;
            final InputMethodSubtype keyboardSubtype = (forceAscii && !asciiCapable)
                    ? SubtypeSwitcher.getInstance().getNoLanguageSubtype()
                    : subtype;
            mParams.mSubtype = keyboardSubtype;
            mParams.mKeyboardLayoutSetName = KEYBOARD_LAYOUT_SET_RESOURCE_PREFIX
                    + SubtypeLocaleUtils.getKeyboardLayoutSetName(keyboardSubtype);
            return this;
        }

        public Builder setIsSpellChecker(final boolean isSpellChecker) {
            mParams.mIsSpellChecker = isSpellChecker;
            return this;
        }

        // TODO: Remove mVoiceKeyOnMain when it's certainly confirmed that we no longer show
        // the voice input key on the symbol layout
        public Builder setOptions(final boolean voiceKeyEnabled, final boolean voiceKeyOnMain,
                final boolean languageSwitchKeyEnabled) {
            @SuppressWarnings("deprecation")
            final boolean deprecatedNoMicrophone = InputAttributes.inPrivateImeOptions(
                    null, NO_MICROPHONE_COMPAT, mParams.mEditorInfo);
            final boolean noMicrophone = InputAttributes.inPrivateImeOptions(
                    mPackageName, NO_MICROPHONE, mParams.mEditorInfo)
                    || deprecatedNoMicrophone;
            mParams.mVoiceKeyEnabled = voiceKeyEnabled && !noMicrophone;
            mParams.mVoiceKeyOnMain = voiceKeyOnMain;
            mParams.mLanguageSwitchKeyEnabled = languageSwitchKeyEnabled;
            return this;
        }

        public void disableTouchPositionCorrectionData() {
            mParams.mDisableTouchPositionCorrectionDataForTest = true;
        }

        public KeyboardLayoutSet build() {
            if (mParams.mSubtype == null)
                throw new RuntimeException("KeyboardLayoutSet subtype is not specified");
            final String packageName = mResources.getResourcePackageName(
                    R.xml.keyboard_layout_set_qwerty);
            final String keyboardLayoutSetName = mParams.mKeyboardLayoutSetName;
            final int xmlId = mResources.getIdentifier(keyboardLayoutSetName, "xml", packageName);
            try {
                parseKeyboardLayoutSet(mResources, xmlId);
            } catch (final IOException e) {
                throw new RuntimeException(e.getMessage() + " in " + keyboardLayoutSetName, e);
            } catch (final XmlPullParserException e) {
                throw new RuntimeException(e.getMessage() + " in " + keyboardLayoutSetName, e);
            }
            return new KeyboardLayoutSet(mContext, mParams);
        }

        private void parseKeyboardLayoutSet(final Resources res, final int resId)
                throws XmlPullParserException, IOException {
            final XmlResourceParser parser = res.getXml(resId);
            try {
                while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                    final int event = parser.next();
                    if (event == XmlPullParser.START_TAG) {
                        final String tag = parser.getName();
                        if (TAG_KEYBOARD_SET.equals(tag)) {
                            parseKeyboardLayoutSetContent(parser);
                        } else {
                            throw new XmlParseUtils.IllegalStartTag(parser, tag, TAG_KEYBOARD_SET);
                        }
                    }
                }
            } finally {
                parser.close();
            }
        }

        private void parseKeyboardLayoutSetContent(final XmlPullParser parser)
                throws XmlPullParserException, IOException {
            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                final int event = parser.next();
                if (event == XmlPullParser.START_TAG) {
                    final String tag = parser.getName();
                    if (TAG_ELEMENT.equals(tag)) {
                        parseKeyboardLayoutSetElement(parser);
                    } else {
                        throw new XmlParseUtils.IllegalStartTag(parser, tag, TAG_KEYBOARD_SET);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    final String tag = parser.getName();
                    if (TAG_KEYBOARD_SET.equals(tag)) {
                        break;
                    } else {
                        throw new XmlParseUtils.IllegalEndTag(parser, tag, TAG_KEYBOARD_SET);
                    }
                }
            }
        }

        private void parseKeyboardLayoutSetElement(final XmlPullParser parser)
                throws XmlPullParserException, IOException {
            final TypedArray a = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.KeyboardLayoutSet_Element);
            try {
                XmlParseUtils.checkAttributeExists(a,
                        R.styleable.KeyboardLayoutSet_Element_elementName, "elementName",
                        TAG_ELEMENT, parser);
                XmlParseUtils.checkAttributeExists(a,
                        R.styleable.KeyboardLayoutSet_Element_elementKeyboard, "elementKeyboard",
                        TAG_ELEMENT, parser);
                XmlParseUtils.checkEndTag(TAG_ELEMENT, parser);

                final ElementParams elementParams = new ElementParams();
                final int elementName = a.getInt(
                        R.styleable.KeyboardLayoutSet_Element_elementName, 0);
                elementParams.mKeyboardXmlId = a.getResourceId(
                        R.styleable.KeyboardLayoutSet_Element_elementKeyboard, 0);
                elementParams.mProximityCharsCorrectionEnabled = a.getBoolean(
                        R.styleable.KeyboardLayoutSet_Element_enableProximityCharsCorrection,
                        false);
                mParams.mKeyboardLayoutSetElementIdToParamsMap.put(elementName, elementParams);
            } finally {
                a.recycle();
            }
        }

        private static int getKeyboardMode(final EditorInfo editorInfo) {
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
                if (InputTypeUtils.isEmailVariation(variation)) {
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
