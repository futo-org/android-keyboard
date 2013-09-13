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

#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_utils.h"

#include "defines.h"
#include "suggest/policyimpl/dictionary/utils/byte_array_utils.h"

namespace latinime {

typedef DynamicPatriciaTrieReadingUtils DptReadingUtils;

const DptReadingUtils::NodeFlags DptReadingUtils::MASK_MOVED = 0xC0;
const DptReadingUtils::NodeFlags DptReadingUtils::FLAG_IS_NOT_MOVED = 0xC0;
const DptReadingUtils::NodeFlags DptReadingUtils::FLAG_IS_MOVED = 0x40;
const DptReadingUtils::NodeFlags DptReadingUtils::FLAG_IS_DELETED = 0x80;

/* static */ int DptReadingUtils::readChildrenPositionAndAdvancePosition(
        const uint8_t *const buffer, int *const pos) {
    const int base = *pos;
    const int offset = ByteArrayUtils::readSint24AndAdvancePosition(buffer, pos);
    if (offset == 0) {
        // 0 offset means that the node does not have children.
        return NOT_A_DICT_POS;
    } else {
        return base + offset;
    }
}

} // namespace latinime
