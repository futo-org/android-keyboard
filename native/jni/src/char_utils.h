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
#include <stdint.h>

namespace latinime {

inline static bool isAsciiUpper(unsigned short c) {
    // Note: isupper(...) reports false positives for some Cyrillic characters, causing them to
    // be incorrectly lower-cased using toAsciiLower(...) rather than latin_tolower(...).
    return (c >= 'A' && c <= 'Z');
}

inline static unsigned short toAsciiLower(unsigned short c) {
    return c - 'A' + 'a';
}

inline static bool isAscii(unsigned short c) {
    return isascii(static_cast<int>(c)) != 0;
}

unsigned short latin_tolower(const unsigned short c);

/**
 * Table mapping most combined Latin, Greek, and Cyrillic characters
 * to their base characters.  If c is in range, BASE_CHARS[c] == c
 * if c is not a combined character, or the base character if it
 * is combined.
 */

static const int BASE_CHARS_SIZE = 0x0500;
extern const uint16_t BASE_CHARS[BASE_CHARS_SIZE];

inline static unsigned short toBaseChar(unsigned short c) {
    if (c < BASE_CHARS_SIZE) {
        return BASE_CHARS[c];
    }
    return c;
}

inline static unsigned short toLowerCase(const unsigned short c) {
    if (isAsciiUpper(c)) {
        return toAsciiLower(c);
    } else if (isAscii(c)) {
        return c;
    }
    return latin_tolower(c);
}

inline static unsigned short toBaseLowerCase(const unsigned short c) {
    return toLowerCase(toBaseChar(c));
}

inline static bool isSkippableChar(const uint16_t character) {
    // TODO: Do not hardcode here
    return character == '\'' || character == '-';
}

} // namespace latinime
#endif // LATINIME_CHAR_UTILS_H
