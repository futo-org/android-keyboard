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

#include "suggest/policyimpl/dictionary/structure/v4/content/shortcut_dict_content.h"

#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {

void ShortcutDictContent::getShortcutEntryAndAdvancePosition(const int maxCodePointCount,
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

int ShortcutDictContent::getShortcutListHeadPos(const int terminalId) const {
    const SparseTable *const addressLookupTable = getAddressLookupTable();
    if (!addressLookupTable->contains(terminalId)) {
        return NOT_A_DICT_POS;
    }
    return addressLookupTable->get(terminalId);
}

bool ShortcutDictContent::flushToFile(const char *const dictDirPath) const {
    return flush(dictDirPath, Ver4DictConstants::SHORTCUT_LOOKUP_TABLE_FILE_EXTENSION,
            Ver4DictConstants::SHORTCUT_CONTENT_TABLE_FILE_EXTENSION,
            Ver4DictConstants::SHORTCUT_FILE_EXTENSION);
}

bool ShortcutDictContent::runGC(
        const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
        const ShortcutDictContent *const originalShortcutDictContent) {
   for (TerminalPositionLookupTable::TerminalIdMap::const_iterator it = terminalIdMap->begin();
           it != terminalIdMap->end(); ++it) {
       const int originalShortcutListPos =
               originalShortcutDictContent->getShortcutListHeadPos(it->first);
       if (originalShortcutListPos == NOT_A_DICT_POS) {
           continue;
       }
       const int shortcutListPos = getContentBuffer()->getTailPosition();
       // Copy shortcut list with GC from original content.
       if (!copyShortcutList(originalShortcutListPos, originalShortcutDictContent,
               shortcutListPos)) {
           return false;
       }
       // Set shortcut list position to the lookup table.
       if (!getUpdatableAddressLookupTable()->set(it->second, shortcutListPos)) {
           return false;
       }
   }
   return true;
}

bool ShortcutDictContent::copyShortcutList(const int shortcutListPos,
        const ShortcutDictContent *const sourceShortcutDictContent, const int toPos) {
    bool hasNext = true;
    int readingPos = shortcutListPos;
    int writingPos = toPos;
    int codePoints[MAX_WORD_LENGTH];
    while (hasNext) {
        int shortcutFlags = 0;
        int codePointCount = 0;
        sourceShortcutDictContent->getShortcutEntryAndAdvancePosition(MAX_WORD_LENGTH,
                codePoints, &codePointCount, &shortcutFlags, &readingPos);
        if (!writeShortcutEntryAndAdvancePosition(codePoints, codePointCount, shortcutFlags,
                &writingPos)) {
            return false;
        }
    }
    return true;
}

bool ShortcutDictContent::writeShortcutEntryAndAdvancePosition(const int *const codePoint,
        const int codePointCount, const int shortcutFlags, int *const shortcutEntryPos) {
    BufferWithExtendableBuffer *const shortcutListBuffer = getWritableContentBuffer();
    if (!shortcutListBuffer->writeUintAndAdvancePosition(shortcutFlags,
            Ver4DictConstants::SHORTCUT_FLAGS_FIELD_SIZE, shortcutEntryPos)) {
        return false;
    }
    if (!shortcutListBuffer->writeCodePointsAndAdvancePosition(codePoint, codePointCount,
            true /* writesTerminator */, shortcutEntryPos)) {
        return false;
    }
    return true;
}

} // namespace latinime
