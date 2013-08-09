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

#include "suggest/policyimpl/dictionary/dictionary_structure_with_buffer_policy_factory.h"

#include "defines.h"
#include "suggest/core/dictionary/binary_dictionary_info.h"
#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_policy.h"
#include "suggest/policyimpl/dictionary/patricia_trie_policy.h"

namespace latinime {

/* static */ DictionaryStructureWithBufferPolicy *DictionaryStructureWithBufferPolicyFactory
        ::newDictionaryStructurePolicy(
        const BinaryDictionaryInfo *const binaryDictionaryInfo) {
    switch (binaryDictionaryInfo->getFormat()) {
        case BinaryDictionaryFormatUtils::VERSION_2:
            return new PatriciaTriePolicy(binaryDictionaryInfo->getDictRoot(),
                    binaryDictionaryInfo);
        case BinaryDictionaryFormatUtils::VERSION_3:
            return new DynamicPatriciaTriePolicy(binaryDictionaryInfo->getDictRoot(),
                    binaryDictionaryInfo);
        default:
            ASSERT(false);
            return 0;
    }
}

} // namespace latinime
