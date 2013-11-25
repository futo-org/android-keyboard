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

#ifndef LATINIME_PROBABILITY_DICT_CONTENT_H
#define LATINIME_PROBABILITY_DICT_CONTENT_H

#include "defines.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/single_dict_content.h"
#include "suggest/policyimpl/dictionary/structure/v4/content/terminal_position_lookup_table.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"
#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {

class ProbabilityDictContent : public SingleDictContent {
 public:
    ProbabilityDictContent(const char *const dictDirPath, const bool isUpdatable)
            : SingleDictContent(dictDirPath, Ver4DictConstants::FREQ_FILE_EXTENSION, isUpdatable),
              mSize(getBuffer()->getTailPosition() / (Ver4DictConstants::PROBABILITY_SIZE
                      + Ver4DictConstants::FLAGS_IN_PROBABILITY_FILE_SIZE)) {}

    ProbabilityDictContent() : mSize(0) {}

    int getProbability(const int terminalId) const {
        if (terminalId < 0 || terminalId >= mSize) {
            return NOT_A_PROBABILITY;
        }
        return Ver4PatriciaTrieReadingUtils::getProbability(getBuffer(), terminalId);
    }

    bool setProbability(const int terminalId, const int probability) {
        if (terminalId < 0) {
            return false;
        }
        if (terminalId >= mSize) {
            // Write new entry.
            int writingPos = getBuffer()->getTailPosition();
            while (writingPos <= getEntryPos(terminalId)) {
                const int dummyFlags = 0;
                if (!getWritableBuffer()->writeUintAndAdvancePosition(dummyFlags,
                        Ver4DictConstants::FLAGS_IN_PROBABILITY_FILE_SIZE, &writingPos)) {
                    return false;
                }
                const int dummyProbability = 0;
                if (!getWritableBuffer()->writeUintAndAdvancePosition(dummyProbability,
                        Ver4DictConstants::PROBABILITY_SIZE, &writingPos)) {
                    return false;
                }
                mSize++;
            }
        }
        const int probabilityWritingPos = getEntryPos(terminalId)
                + Ver4DictConstants::FLAGS_IN_PROBABILITY_FILE_SIZE;
        return getWritableBuffer()->writeUint(probability,
                Ver4DictConstants::PROBABILITY_SIZE, probabilityWritingPos);
    }

    bool flushToFile(const char *const dictDirPath) const {
        if (getEntryPos(mSize) < getBuffer()->getTailPosition()) {
            ProbabilityDictContent probabilityDictContentToWrite;
            for (int i = 0; i < mSize; ++i) {
                if (!probabilityDictContentToWrite.setProbability(i, getProbability(i))) {
                    return false;
                }
            }
            return probabilityDictContentToWrite.flush(dictDirPath,
                    Ver4DictConstants::FREQ_FILE_EXTENSION);
        } else {
            return flush(dictDirPath, Ver4DictConstants::FREQ_FILE_EXTENSION);
        }
    }

    bool runGC(const TerminalPositionLookupTable::TerminalIdMap *const terminalIdMap,
            const ProbabilityDictContent *const originalProbabilityDictContent) {
        mSize = 0;
        for (TerminalPositionLookupTable::TerminalIdMap::const_iterator it = terminalIdMap->begin();
                it != terminalIdMap->end(); ++it) {
            if (!setProbability(it->second,
                    originalProbabilityDictContent->getProbability(it->first))) {
                return false;
            }
            mSize++;
        }
        return true;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(ProbabilityDictContent);

    int getEntryPos(const int terminalId) const {
        return terminalId * (Ver4DictConstants::FLAGS_IN_PROBABILITY_FILE_SIZE
                + Ver4DictConstants::PROBABILITY_SIZE);
    }

    int mSize;
};
} // namespace latinime
#endif /* LATINIME_PROBABILITY_DICT_CONTENT_H */
