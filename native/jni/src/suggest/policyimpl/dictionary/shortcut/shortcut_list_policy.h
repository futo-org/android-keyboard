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

#ifndef LATINIME_SHORTCUT_LIST_POLICY_H
#define LATINIME_SHORTCUT_LIST_POLICY_H

#include <stdint.h>

#include "defines.h"
#include "suggest/core/policy/dictionary_shortcuts_structure_policy.h"
// TODO: Move shortcuts reading methods to policyimpl.
#include "suggest/core/dictionary/binary_dictionary_terminal_attributes_reading_utils.h"

namespace latinime {

class ShortcutListPolicy : public DictionaryShortcutsStructurePolicy {
 public:
    explicit ShortcutListPolicy(const uint8_t *const shortcutBuf)
            : mShortcutsBuf(shortcutBuf) {}

    ~ShortcutListPolicy() {}

    int getStartPos(const int pos) const {
        int listPos = pos;
        BinaryDictionaryTerminalAttributesReadingUtils::getShortcutListSizeAndForwardPointer(
                mShortcutsBuf, &listPos);
        return listPos;
    }

    void getNextShortcut(const int maxCodePointCount, int *const outCodePoint,
            int *const outCodePointCount, bool *const outIsWhitelist, bool *const outHasNext,
            int *const pos) const {
        const BinaryDictionaryTerminalAttributesReadingUtils::ShortcutFlags flags =
                BinaryDictionaryTerminalAttributesReadingUtils::getFlagsAndForwardPointer(
                        mShortcutsBuf, pos);
        if (outHasNext) {
            *outHasNext = BinaryDictionaryTerminalAttributesReadingUtils::hasNext(flags);
        }
        if (outIsWhitelist) {
            *outIsWhitelist =
                    BinaryDictionaryTerminalAttributesReadingUtils::isWhitelist(flags);
        }
        if (outCodePoint) {
            *outCodePointCount =
                    BinaryDictionaryTerminalAttributesReadingUtils::readShortcutTarget(
                            mShortcutsBuf, maxCodePointCount, outCodePoint, pos);
        }
    }

    void skipAllShortcuts(int *const pos) const {
        const int shortcutListSize = BinaryDictionaryTerminalAttributesReadingUtils
                ::getShortcutListSizeAndForwardPointer(mShortcutsBuf, pos);
        *pos += shortcutListSize;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ShortcutListPolicy);

    const uint8_t *const mShortcutsBuf;
};
} // namespace latinime
#endif // LATINIME_SHORTCUT_LIST_POLICY_H
