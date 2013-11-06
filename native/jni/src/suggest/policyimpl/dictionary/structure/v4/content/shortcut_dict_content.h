/*
 * Copyright (C) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef LATINIME_SHORTCUT_DICT_CONTENT_H
#define LATINIME_SHORTCUT_DICT_CONTENT_H

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/sparse_table_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"

namespace latinime {

class ShortcutDictContent : public SparseTableDictContent {
 public:
    ShortcutDictContent(const char *const dictDirPath, const bool isUpdatable)
            : SparseTableDictContent(dictDirPath,
                      Ver4DictConstants::SHORTCUT_LOOKUP_TABLE_FILE_EXTENSION,
                      Ver4DictConstants::SHORTCUT_CONTENT_TABLE_FILE_EXTENSION,
                      Ver4DictConstants::SHORTCUT_FILE_EXTENSION, isUpdatable,
                      Ver4DictConstants::SHORTCUT_ADDRESS_TABLE_BLOCK_SIZE,
                      Ver4DictConstants::SHORTCUT_ADDRESS_TABLE_DATA_SIZE) {}

    void getShortcutEntryAndAdvancePosition(const int maxCodePointCount,
            int *const outCodePoint, int *const outCodePointCount, int *const outShortcutFlags,
            int *const shortcutEntryPos) const {
        const BufferWithExtendableBuffer *const shortcutListBuffer = getContentBuffer();
        if (outShortcutFlags) {
            *outShortcutFlags = shortcutListBuffer->readUintAndAdvancePosition(
                    Ver4DictConstants::SHORTCUT_FLAGS_FIELD_SIZE, shortcutEntryPos);
        }
        if (outCodePoint && outCodePointCount) {
            shortcutListBuffer->readCodePointsAndAdvancePosition(
                    maxCodePointCount, outCodePoint, outCodePointCount, shortcutEntryPos);
        }
    }

   // Returns head position of shortcut list for a PtNode specified by terminalId.
   int getShortcutListHeadPos(const int terminalId) const {
        const SparseTable *const addressLookupTable = getAddressLookupTable();
        if (!addressLookupTable->contains(terminalId)) {
            return NOT_A_DICT_POS;
        }
        return addressLookupTable->get(terminalId);
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ShortcutDictContent);
};
} // namespace latinime
#endif /* LATINIME_SHORTCUT_DICT_CONTENT_H */
