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

#include <stdint.h>

#include "suggest/core/policy/dictionary_shortcuts_structure_policy.h"

namespace latinime {

/**
 * This class encapsulates information about a terminal that allows to
 * retrieve local node attributes like the list of shortcuts without
 * exposing the format structure to the client.
 */
class TerminalAttributes {
 public:
    class ShortcutIterator {
     public:
        ShortcutIterator(const DictionaryShortcutsStructurePolicy *const shortcutStructurePolicy,
                const int shortcutPos, const bool hasShortcutList)
                : mShortcutStructurePolicy(shortcutStructurePolicy), mPos(shortcutPos),
                  mHasNextShortcutTarget(hasShortcutList) {}

        inline bool hasNextShortcutTarget() const {
            return mHasNextShortcutTarget;
        }

        // Gets the shortcut target itself as an int string and put it to outTarget, put its length
        // to outTargetLength, put whether it is whitelist to outIsWhitelist.
        AK_FORCE_INLINE void nextShortcutTarget(
                const int maxDepth, int *const outTarget, int *const outTargetLength,
                bool *const outIsWhitelist) {
            mShortcutStructurePolicy->getNextShortcut(maxDepth, outTarget, outTargetLength,
                    outIsWhitelist, &mHasNextShortcutTarget, &mPos);
        }

     private:
        const DictionaryShortcutsStructurePolicy *const mShortcutStructurePolicy;
        int mPos;
        bool mHasNextShortcutTarget;
    };

    TerminalAttributes(const DictionaryShortcutsStructurePolicy *const shortcutStructurePolicy,
            const int shortcutPos)
            : mShortcutStructurePolicy(shortcutStructurePolicy),
              mShortcutListSizePos(shortcutPos) {}

    inline ShortcutIterator getShortcutIterator() const {
        int shortcutPos = mShortcutListSizePos;
        const bool hasShortcutList = shortcutPos != NOT_A_DICT_POS;
        if (hasShortcutList) {
            shortcutPos = mShortcutStructurePolicy->getStartPos(shortcutPos);
        }
        // shortcutPos is never used if hasShortcutList is false.
        return ShortcutIterator(mShortcutStructurePolicy, shortcutPos, hasShortcutList);
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(TerminalAttributes);
    const DictionaryShortcutsStructurePolicy *const mShortcutStructurePolicy;
    const int mShortcutListSizePos;
};
} // namespace latinime
#endif // LATINIME_TERMINAL_ATTRIBUTES_H
