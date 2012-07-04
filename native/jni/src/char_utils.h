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

namespace latinime {

inline static int isAsciiUpper(unsigned short c) {
    return c >= 'A' && c <= 'Z';
}

inline static unsigned short toAsciiLower(unsigned short c) {
    return c - 'A' + 'a';
}

inline static int isAscii(unsigned short c) {
    return c <= 127;
}

unsigned short latin_tolower(unsigned short c);

/**
 * Table mapping most combined Latin, Greek, and Cyrillic characters
 * to their base characters.  If c is in range, BASE_CHARS[c] == c
 * if c is not a combined character, or the base character if it
 * is combined.
 */

static const int BASE_CHARS_SIZE = 0x0500;
extern const unsigned short BASE_CHARS[BASE_CHARS_SIZE];

inline static unsigned short toBaseChar(unsigned short c) {
    if (c < BASE_CHARS_SIZE) {
        return BASE_CHARS[c];
    }
    return c;
}

inline static unsigned short toBaseLowerCase(unsigned short c) {
    c = toBaseChar(c);
    if (isAsciiUpper(c)) {
        return toAsciiLower(c);
    } else if (isAscii(c)) {
        return c;
    }
    return latin_tolower(c);
}

} // namespace latinime

#endif // LATINIME_CHAR_UTILS_H
