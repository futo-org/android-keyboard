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
        int mPos;

     public:
        ShortcutIterator(const uint8_t* const dict, const int pos) : mDict(dict), mPos(pos) {
        }

        inline bool hasNextShortcutTarget() const {
            // TODO: stub method. Fill this in.
            return false;
        }

        inline int getNextShortcutTarget(const int maxDepth, uint16_t* outWord) {
            // TODO: stub method. Fill this in.
            return 0;
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

    inline bool isShortcutOnly() const {
        // TODO: stub method. Fill this in.
        return false;
    }

    inline ShortcutIterator getShortcutIterator() const {
        return ShortcutIterator(mDict, mStartPos);
    }
};
} // namespace latinime

#endif // LATINIME_TERMINAL_ATTRIBUTES_H
