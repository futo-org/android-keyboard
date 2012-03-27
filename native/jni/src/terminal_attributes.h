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

#ifndef LATINIME_TERMINAL_ATTRIBUTES_H
#define LATINIME_TERMINAL_ATTRIBUTES_H

#include "unigram_dictionary.h"

namespace latinime {

/**
 * This class encapsulates information about a terminal that allows to
 * retrieve local node attributes like the list of shortcuts without
 * exposing the format structure to the client.
 */
class TerminalAttributes {
 public:
    class ShortcutIterator {
        const uint8_t* const mDict;
        bool mHasNextShortcutTarget;
        int mPos;

     public:
        ShortcutIterator(const uint8_t* dict, const int pos, const uint8_t flags) : mDict(dict),
                mPos(pos) {
            mHasNextShortcutTarget = (0 != (flags & UnigramDictionary::FLAG_HAS_SHORTCUT_TARGETS));
        }

        inline bool hasNextShortcutTarget() const {
            return mHasNextShortcutTarget;
        }

        // Gets the shortcut target itself as a uint16_t string. For parameters and return value
        // see BinaryFormat::getWordAtAddress.
        // TODO: make the output an uint32_t* to handle the whole unicode range.
        inline int getNextShortcutTarget(const int maxDepth, uint16_t* outWord) {
            const int shortcutFlags = BinaryFormat::getFlagsAndForwardPointer(mDict, &mPos);
            mHasNextShortcutTarget =
                    0 != (shortcutFlags & UnigramDictionary::FLAG_ATTRIBUTE_HAS_NEXT);
            unsigned int i;
            for (i = 0; i < MAX_WORD_LENGTH_INTERNAL; ++i) {
                const int charCode = BinaryFormat::getCharCodeAndForwardPointer(mDict, &mPos);
                if (NOT_A_CHARACTER == charCode) break;
                outWord[i] = (uint16_t)charCode;
            }
            mPos += BinaryFormat::CHARACTER_ARRAY_TERMINATOR_SIZE;
            return i;
        }
    };

 private:
    const uint8_t* const mDict;
    const uint8_t mFlags;
    const int mStartPos;

 public:
    TerminalAttributes(const uint8_t* const dict, const uint8_t flags, const int pos) :
            mDict(dict), mFlags(flags), mStartPos(pos) {
    }

    inline ShortcutIterator getShortcutIterator() const {
        // The size of the shortcuts is stored here so that the whole shortcut chunk can be
        // skipped quickly, so we ignore it.
        return ShortcutIterator(mDict, mStartPos + BinaryFormat::SHORTCUT_LIST_SIZE_SIZE, mFlags);
    }
};
} // namespace latinime

#endif // LATINIME_TERMINAL_ATTRIBUTES_H
