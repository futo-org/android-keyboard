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

#include "suggest/policyimpl/dictionary/patricia_trie_reading_utils.h"

#include "defines.h"
#include "suggest/policyimpl/dictionary/utils/byte_array_utils.h"

namespace latinime {

typedef PatriciaTrieReadingUtils PtReadingUtils;

const PtReadingUtils::NodeFlags PtReadingUtils::MASK_CHILDREN_POSITION_TYPE = 0xC0;
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_CHILDREN_POSITION_TYPE_NOPOSITION = 0x00;
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_CHILDREN_POSITION_TYPE_ONEBYTE = 0x40;
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_CHILDREN_POSITION_TYPE_TWOBYTES = 0x80;
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_CHILDREN_POSITION_TYPE_THREEBYTES = 0xC0;

// Flag for single/multiple char group
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_HAS_MULTIPLE_CHARS = 0x20;
// Flag for terminal PtNodes
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_IS_TERMINAL = 0x10;
// Flag for shortcut targets presence
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_HAS_SHORTCUT_TARGETS = 0x08;
// Flag for bigram presence
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_HAS_BIGRAMS = 0x04;
// Flag for non-words (typically, shortcut only entries)
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_IS_NOT_A_WORD = 0x02;
// Flag for blacklist
const PtReadingUtils::NodeFlags PtReadingUtils::FLAG_IS_BLACKLISTED = 0x01;

/* static */ int PtReadingUtils::readChildrenPositionAndAdvancePosition(
        const uint8_t *const buffer, const NodeFlags flags, int *const pos) {
    const int base = *pos;
    int offset = 0;
    switch (MASK_CHILDREN_POSITION_TYPE & flags) {
        case FLAG_CHILDREN_POSITION_TYPE_ONEBYTE:
            offset = ByteArrayUtils::readUint8AndAdvancePosition(buffer, pos);
            break;
        case FLAG_CHILDREN_POSITION_TYPE_TWOBYTES:
            offset = ByteArrayUtils::readUint16AndAdvancePosition(buffer, pos);
            break;
        case FLAG_CHILDREN_POSITION_TYPE_THREEBYTES:
            offset = ByteArrayUtils::readUint24AndAdvancePosition(buffer, pos);
            break;
        default:
            // If we come here, it means we asked for the children of a word with
            // no children.
            return NOT_A_DICT_POS;
    }
    return base + offset;
}

} // namespace latinime
