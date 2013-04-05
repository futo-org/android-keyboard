/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef DIGRAPH_UTILS_H
#define DIGRAPH_UTILS_H

namespace latinime {

class DigraphUtils {
 public:
    typedef enum {
        NOT_A_DIGRAPH_INDEX,
        FIRST_DIGRAPH_CODEPOINT,
        SECOND_DIGRAPH_CODEPOINT
    } DigraphCodePointIndex;

    typedef struct { int first; int second; int compositeGlyph; } digraph_t;

    static bool hasDigraphForCodePoint(const int dictFlags, const int compositeGlyphCodePoint);
    static int getAllDigraphsForDictionaryAndReturnSize(
            const int dictFlags, const digraph_t **digraphs);
    static int getDigraphCodePointForIndex(const int dictFlags, const int compositeGlyphCodePoint,
            const DigraphCodePointIndex digraphCodePointIndex);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DigraphUtils);
    static const digraph_t *getDigraphForCodePoint(
            const int dictFlags, const int compositeGlyphCodePoint);

    static const digraph_t GERMAN_UMLAUT_DIGRAPHS[];
    static const digraph_t FRENCH_LIGATURES_DIGRAPHS[];
};
} // namespace latinime
#endif // DIGRAPH_UTILS_H
