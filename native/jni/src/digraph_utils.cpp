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

#include "binary_format.h"
#include "defines.h"
#include "digraph_utils.h"

namespace latinime {

const DigraphUtils::digraph_t DigraphUtils::GERMAN_UMLAUT_DIGRAPHS[] =
        { { 'a', 'e', 0x00E4 }, // U+00E4 : LATIN SMALL LETTER A WITH DIAERESIS
        { 'o', 'e', 0x00F6 },   // U+00F6 : LATIN SMALL LETTER O WITH DIAERESIS
        { 'u', 'e', 0x00FC } }; // U+00FC : LATIN SMALL LETTER U WITH DIAERESIS
const DigraphUtils::digraph_t DigraphUtils::FRENCH_LIGATURES_DIGRAPHS[] =
        { { 'a', 'e', 0x00E6 }, // U+00E6 : LATIN SMALL LETTER AE
        { 'o', 'e', 0x0153 } }; // U+0153 : LATIN SMALL LIGATURE OE

/* static */ bool DigraphUtils::hasDigraphForCodePoint(
        const int dictFlags, const int compositeGlyphCodePoint) {
    if (DigraphUtils::getDigraphForCodePoint(dictFlags, compositeGlyphCodePoint)) {
        return true;
    }
    return false;
}

// Retrieves the set of all digraphs associated with the given dictionary.
// Returns the size of the digraph array, or 0 if none exist.
/* static */ int DigraphUtils::getAllDigraphsForDictionaryAndReturnSize(
        const int dictFlags, const DigraphUtils::digraph_t **digraphs) {
    if (BinaryFormat::REQUIRES_GERMAN_UMLAUT_PROCESSING & dictFlags) {
        *digraphs = DigraphUtils::GERMAN_UMLAUT_DIGRAPHS;
        return NELEMS(DigraphUtils::GERMAN_UMLAUT_DIGRAPHS);
    }
    if (BinaryFormat::REQUIRES_FRENCH_LIGATURES_PROCESSING & dictFlags) {
        *digraphs = DigraphUtils::FRENCH_LIGATURES_DIGRAPHS;
        return NELEMS(DigraphUtils::FRENCH_LIGATURES_DIGRAPHS);
    }
    return 0;
}

// Returns the digraph codepoint for the given composite glyph codepoint and digraph codepoint index
// (which specifies the first or second codepoint in the digraph).
/* static */ int DigraphUtils::getDigraphCodePointForIndex(const int dictFlags,
        const int compositeGlyphCodePoint, const DigraphCodePointIndex digraphCodePointIndex) {
    if (digraphCodePointIndex == NOT_A_DIGRAPH_INDEX) {
        return NOT_A_CODE_POINT;
    }
    const DigraphUtils::digraph_t *digraph =
            DigraphUtils::getDigraphForCodePoint(dictFlags, compositeGlyphCodePoint);
    if (!digraph) {
        return NOT_A_CODE_POINT;
    }
    if (digraphCodePointIndex == FIRST_DIGRAPH_CODEPOINT) {
        return digraph->first;
    } else if (digraphCodePointIndex == SECOND_DIGRAPH_CODEPOINT) {
        return digraph->second;
    }
    ASSERT(false);
    return NOT_A_CODE_POINT;
}

/**
 * Returns the digraph for the input composite glyph codepoint, or 0 if none exists.
 * dictFlags: the dictionary flags needed to determine which digraphs are supported.
 * compositeGlyphCodePoint: the method returns the digraph corresponding to this codepoint.
 */
/* static */ const DigraphUtils::digraph_t *DigraphUtils::getDigraphForCodePoint(
        const int dictFlags, const int compositeGlyphCodePoint) {
    const DigraphUtils::digraph_t *digraphs = 0;
    const int digraphsSize =
            DigraphUtils::getAllDigraphsForDictionaryAndReturnSize(dictFlags, &digraphs);
    for (int i = 0; i < digraphsSize; i++) {
        if (digraphs[i].compositeGlyph == compositeGlyphCodePoint) {
            return &digraphs[i];
        }
    }
    return 0;
}

} // namespace latinime
