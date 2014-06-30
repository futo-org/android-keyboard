/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.util.Locale;
import java.util.TreeMap;

/**
 * A class to help with handling different writing scripts.
 */
public class ScriptUtils {
    // Used for hardware keyboards
    public static final int SCRIPT_UNKNOWN = -1;
    // TODO: should we use ISO 15924 identifiers instead?
    public static final int SCRIPT_LATIN = 0;
    public static final int SCRIPT_CYRILLIC = 1;
    public static final int SCRIPT_GREEK = 2;
    public static final int SCRIPT_ARABIC = 3;
    public static final int SCRIPT_HEBREW = 4;
    public static final TreeMap<String, Integer> mLanguageToScript;
    static {
        // List of the supported languages and their associated script. We won't check
        // words written in another script than the selected script, because we know we
        // don't have those in our dictionary so we will underline everything and we
        // will never have any suggestions, so it makes no sense checking them, and this
        // is done in {@link #shouldFilterOut}. Also, the script is used to choose which
        // proximity to pass to the dictionary descent algorithm.
        // IMPORTANT: this only contains languages - do not write countries in there.
        // Only the language is searched from the map.
        mLanguageToScript = new TreeMap<>();
        mLanguageToScript.put("cs", SCRIPT_LATIN);
        mLanguageToScript.put("da", SCRIPT_LATIN);
        mLanguageToScript.put("de", SCRIPT_LATIN);
        mLanguageToScript.put("el", SCRIPT_GREEK);
        mLanguageToScript.put("en", SCRIPT_LATIN);
        mLanguageToScript.put("es", SCRIPT_LATIN);
        mLanguageToScript.put("fi", SCRIPT_LATIN);
        mLanguageToScript.put("fr", SCRIPT_LATIN);
        mLanguageToScript.put("hr", SCRIPT_LATIN);
        mLanguageToScript.put("it", SCRIPT_LATIN);
        mLanguageToScript.put("lt", SCRIPT_LATIN);
        mLanguageToScript.put("lv", SCRIPT_LATIN);
        mLanguageToScript.put("nb", SCRIPT_LATIN);
        mLanguageToScript.put("nl", SCRIPT_LATIN);
        mLanguageToScript.put("pt", SCRIPT_LATIN);
        mLanguageToScript.put("sl", SCRIPT_LATIN);
        mLanguageToScript.put("ru", SCRIPT_CYRILLIC);
    }
    /*
     * Returns whether the code point is a letter that makes sense for the specified
     * locale for this spell checker.
     * The dictionaries supported by Latin IME are described in res/xml/spellchecker.xml
     * and is limited to EFIGS languages and Russian.
     * Hence at the moment this explicitly tests for Cyrillic characters or Latin characters
     * as appropriate, and explicitly excludes CJK, Arabic and Hebrew characters.
     */
    public static boolean isLetterCheckableByScript(final int codePoint, final int script) {
        switch (script) {
        case SCRIPT_LATIN:
            // Our supported latin script dictionaries (EFIGS) at the moment only include
            // characters in the C0, C1, Latin Extended A and B, IPA extensions unicode
            // blocks. As it happens, those are back-to-back in the code range 0x40 to 0x2AF,
            // so the below is a very efficient way to test for it. As for the 0-0x3F, it's
            // excluded from isLetter anyway.
            return codePoint <= 0x2AF && Character.isLetter(codePoint);
        case SCRIPT_CYRILLIC:
            // All Cyrillic characters are in the 400~52F block. There are some in the upper
            // Unicode range, but they are archaic characters that are not used in modern
            // Russian and are not used by our dictionary.
            return codePoint >= 0x400 && codePoint <= 0x52F && Character.isLetter(codePoint);
        case SCRIPT_GREEK:
            // Greek letters are either in the 370~3FF range (Greek & Coptic), or in the
            // 1F00~1FFF range (Greek extended). Our dictionary contains both sort of characters.
            // Our dictionary also contains a few words with 0xF2; it would be best to check
            // if that's correct, but a web search does return results for these words so
            // they are probably okay.
            return (codePoint >= 0x370 && codePoint <= 0x3FF)
                    || (codePoint >= 0x1F00 && codePoint <= 0x1FFF)
                    || codePoint == 0xF2;
        case SCRIPT_UNKNOWN:
            return true;
        default:
            // Should never come here
            throw new RuntimeException("Impossible value of script: " + script);
        }
    }

    public static int getScriptFromLocale(final Locale locale) {
        final Integer script = mLanguageToScript.get(locale.getLanguage());
        if (null == script) {
            throw new RuntimeException("We have been called with an unsupported language: \""
                    + locale.getLanguage() + "\". Framework bug?");
        }
        return script;
    }
}
