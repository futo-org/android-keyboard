/*
 * Copyright (C) 2014, The Android Open Source Project
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

#include "suggest/policyimpl/dictionary/structure/v2/ver2_pt_node_array_reader.h"

#include "suggest/policyimpl/dictionary/structure/pt_common/patricia_trie_reading_utils.h"

namespace latinime {

bool Ver2PtNodeArrayReader::readPtNodeArrayInfoAndReturnIfValid(const int ptNodeArrayPos,
        int *const outPtNodeCount, int *const outFirstPtNodePos) const {
    if (ptNodeArrayPos < 0 || ptNodeArrayPos >= mDictSize) {
        // Reading invalid position because of a bug or a broken dictionary.
        AKLOGE("Reading PtNode array info from invalid dictionary position: %d, dict size: %d",
                ptNodeArrayPos, mDictSize);
        ASSERT(false);
        return false;
    }
    int readingPos = ptNodeArrayPos;
    const int ptNodeCountInArray = PatriciaTrieReadingUtils::getPtNodeArraySizeAndAdvancePosition(
            mDictBuffer, &readingPos);
    *outPtNodeCount = ptNodeCountInArray;
    *outFirstPtNodePos = readingPos;
    return true;
}

bool Ver2PtNodeArrayReader::readForwardLinkAndReturnIfValid(const int forwordLinkPos,
        int *const outNextPtNodeArrayPos) const {
    if (forwordLinkPos < 0 || forwordLinkPos >= mDictSize) {
        // Reading invalid position because of bug or broken dictionary.
        AKLOGE("Reading forward link from invalid dictionary position: %d, dict size: %d",
                forwordLinkPos, mDictSize);
        ASSERT(false);
        return false;
    }
    // Ver2 dicts don't have forward links.
    *outNextPtNodeArrayPos = NOT_A_DICT_POS;
    return true;
}

} // namespace latinime
