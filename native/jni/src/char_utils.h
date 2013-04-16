/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef LATINIME_CHAR_UTILS_H
#define LATINIME_CHAR_UTILS_H

#include <cctype>

#include "defines.h"

namespace latinime {

inline static bool isAsciiUpper(int c) {
    // Note: isupper(...) reports false positives for some Cyrillic characters, causing them to
    // be incorrectly lower-cased using toAsciiLower(...) rather than latin_tolower(...).
    return (c >= 'A' && c <= 'Z');
}

inline static int toAsciiLower(int c) {
    return c - 'A' + 'a';
}

inline static bool isAscii(int c) {
    return isascii(c) != 0;
}

unsigned short latin_tolower(const unsigned short c);

/**
 * Table mapping most combined Latin, Greek, and Cyrillic characters
 * to their base characters.  If c is in range, BASE_CHARS[c] == c
 * if c is not a combined character, or the base character if it
 * is combined.
 */
static const int BASE_CHARS_SIZE = 0x0500;
extern const unsigned short BASE_CHARS[BASE_CHARS_SIZE];

inline static int toBaseCodePoint(int c) {
    if (c < BASE_CHARS_SIZE) {
        return static_cast<int>(BASE_CHARS[c]);
    }
    return c;
}

AK_FORCE_INLINE static int toLowerCase(const int c) {
    if (isAsciiUpper(c)) {
        return toAsciiLower(c);
    }
    if (isAscii(c)) {
        return c;
    }
    return static_cast<int>(latin_tolower(static_cast<unsigned short>(c)));
}

AK_FORCE_INLINE static int toBaseLowerCase(const int c) {
    return toLowerCase(toBaseCodePoint(c));
}

inline static bool isIntentionalOmissionCodePoint(const int codePoint) {
    // TODO: Do not hardcode here
    return codePoint == KEYCODE_SINGLE_QUOTE || codePoint == KEYCODE_HYPHEN_MINUS;
}

inline static int getCodePointCount(const int arraySize, const int *const codePoints) {
    int size = 0;
    for (; size < arraySize; ++size) {
        if (codePoints[size] == '\0') {
            break;
        }
    }
    return size;
}

} // namespace latinime
#endif // LATINIME_CHAR_UTILS_H
