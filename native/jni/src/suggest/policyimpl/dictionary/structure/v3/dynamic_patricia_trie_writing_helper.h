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

#ifndef LATINIME_DYNAMIC_PATRICIA_TRIE_WRITING_HELPER_H
#define LATINIME_DYNAMIC_PATRICIA_TRIE_WRITING_HELPER_H

#include <stdint.h>

#include "defines.h"
#include "utils/hash_map_compat.h"

namespace latinime {

class BufferWithExtendableBuffer;
class DynamicBigramListPolicy;
class DynamicPatriciaTrieReadingHelper;
class DynamicShortcutListPolicy;
class HeaderPolicy;
class PtNodeReader;
class PtNodeWriter;

// TODO: Make it independent from a particular format and move to pt_common.
class DynamicPatriciaTrieWritingHelper {
 public:
    typedef hash_map_compat<int, int> PtNodeArrayPositionRelocationMap;
    typedef hash_map_compat<int, int> PtNodePositionRelocationMap;
    struct DictPositionRelocationMap {
     public:
        DictPositionRelocationMap()
                : mPtNodeArrayPositionRelocationMap(), mPtNodePositionRelocationMap() {}

        PtNodeArrayPositionRelocationMap mPtNodeArrayPositionRelocationMap;
        PtNodePositionRelocationMap mPtNodePositionRelocationMap;

     private:
        DISALLOW_COPY_AND_ASSIGN(DictPositionRelocationMap);
    };

    static const size_t MAX_DICTIONARY_SIZE;

    DynamicPatriciaTrieWritingHelper(BufferWithExtendableBuffer *const buffer,
            const PtNodeReader *const ptNodeReader, PtNodeWriter *const ptNodeWriter,
            DynamicBigramListPolicy *const bigramPolicy,
            DynamicShortcutListPolicy *const shortcutPolicy, const bool needsToDecay)
            : mBuffer(buffer), mPtNodeReader(ptNodeReader), mPtNodeWriter(ptNodeWriter),
              mBigramPolicy(bigramPolicy), mShortcutPolicy(shortcutPolicy),
              mNeedsToDecay(needsToDecay) {}

    ~DynamicPatriciaTrieWritingHelper() {}

    void writeToDictFile(const char *const fileName, const HeaderPolicy *const headerPolicy,
            const int unigramCount, const int bigramCount);

    void writeToDictFileWithGC(const int rootPtNodeArrayPos, const char *const fileName,
            const HeaderPolicy *const headerPolicy);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DynamicPatriciaTrieWritingHelper);

    BufferWithExtendableBuffer *const mBuffer;
    const PtNodeReader *const mPtNodeReader;
    PtNodeWriter *const mPtNodeWriter;
    DynamicBigramListPolicy *const mBigramPolicy;
    DynamicShortcutListPolicy *const mShortcutPolicy;
    const bool mNeedsToDecay;

    bool runGC(const int rootPtNodeArrayPos, const HeaderPolicy *const headerPolicy,
            BufferWithExtendableBuffer *const bufferToWrite, int *const outUnigramCount,
            int *const outBigramCount);
};
} // namespace latinime
#endif /* LATINIME_DYNAMIC_PATRICIA_TRIE_WRITING_HELPER_H */
