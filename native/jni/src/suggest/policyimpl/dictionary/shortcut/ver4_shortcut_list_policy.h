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

#ifndef LATINIME_VER4_SHORTCUT_LIST_POLICY_H
#define LATINIME_VER4_SHORTCUT_LIST_POLICY_H

#include <stdint.h>

#include "defines.h"
#include "suggest/core/policy/dictionary_shortcuts_structure_policy.h"
#include "suggest/policyimpl/dictionary/shortcut/shortcut_list_reading_utils.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/shortcut_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/terminal_position_lookup_table.h"

namespace latinime {

class Ver4ShortcutListPolicy : public DictionaryShortcutsStructurePolicy {
 public:
    Ver4ShortcutListPolicy(const ShortcutDictContent *const shortcutDictContent,
            const TerminalPositionLookupTable *const terminalPositionLookupTable)
            : mShortcutDictContent(shortcutDictContent),
              mTerminalPositionLookupTable(terminalPositionLookupTable) {}

    ~Ver4ShortcutListPolicy() {}

    int getStartPos(const int pos) const {
        // The first shortcut entry is located at the head position of the shortcut list.
        return pos;
    }

    void getNextShortcut(const int maxCodePointCount, int *const outCodePoint,
            int *const outCodePointCount, bool *const outIsWhitelist, bool *const outHasNext,
            int *const pos) const {
        int shortcutFlags = 0;
        if (outCodePoint && outCodePointCount) {
            mShortcutDictContent->getShortcutEntryAndAdvancePosition(maxCodePointCount,
                    outCodePoint, outCodePointCount, &shortcutFlags, pos);
        }
        if (outHasNext) {
            *outHasNext = ShortcutListReadingUtils::hasNext(shortcutFlags);
        }
        if (outIsWhitelist) {
            *outIsWhitelist = ShortcutListReadingUtils::isWhitelist(shortcutFlags);
        }
    }

    void skipAllShortcuts(int *const pos) const {
        // Do nothing because we don't need to skip shortcut lists in ver4 dictionaries.
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(Ver4ShortcutListPolicy);

    const ShortcutDictContent *const mShortcutDictContent;
    const TerminalPositionLookupTable *const mTerminalPositionLookupTable;
};
} // namespace latinime
#endif // LATINIME_VER4_SHORTCUT_LIST_POLICY_H
