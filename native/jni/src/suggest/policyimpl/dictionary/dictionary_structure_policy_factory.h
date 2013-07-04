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

#ifndef LATINIME_DICTIONARY_STRUCTURE_POLICY_FACTORY_H
#define LATINIME_DICTIONARY_STRUCTURE_POLICY_FACTORY_H

#include "defines.h"
#include "suggest/core/dictionary/binary_dictionary_format_utils.h"
#include "suggest/policyimpl/dictionary/patricia_trie_policy.h"

namespace latinime {

class DictionaryStructurePolicy;

class DictionaryStructurePolicyFactory {
 public:
    static const DictionaryStructurePolicy *getDictionaryStructurePolicy(
            const BinaryDictionaryFormatUtils::FORMAT_VERSION dictionaryFormat) {
        switch (dictionaryFormat) {
            case BinaryDictionaryFormatUtils::VERSION_2:
                return PatriciaTriePolicy::getInstance();
            case BinaryDictionaryFormatUtils::VERSION_3:
                // TODO: support version 3 dictionaries.
                return 0;
            default:
                ASSERT(false);
                return 0;
        }
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DictionaryStructurePolicyFactory);
};
} // namespace latinime
#endif // LATINIME_DICTIONARY_STRUCTURE_POLICY_FACTORY_H
