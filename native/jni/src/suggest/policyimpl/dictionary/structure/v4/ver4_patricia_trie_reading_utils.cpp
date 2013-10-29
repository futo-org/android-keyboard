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

#include "suggest/policyimpl/dictionary/structure/v4/ver4_patricia_trie_reading_utils.h"

#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {

/* static */ int Ver4PatriciaTrieReadingUtils::getProbability(
        const BufferWithExtendableBuffer *const probabilityBuffer, const int terminalId) {
    int pos = terminalId * (Ver4DictConstants::FLAGS_IN_PROBABILITY_FILE_SIZE
            + Ver4DictConstants::PROBABILITY_SIZE)
                    + Ver4DictConstants::FLAGS_IN_PROBABILITY_FILE_SIZE;
    return probabilityBuffer->readUintAndAdvancePosition(Ver4DictConstants::PROBABILITY_SIZE, &pos);
}

} // namespace latinime
