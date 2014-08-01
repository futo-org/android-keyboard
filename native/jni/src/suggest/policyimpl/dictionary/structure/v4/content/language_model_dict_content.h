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

#ifndef LATINIME_LANGUAGE_MODEL_DICT_CONTENT_H
#define LATINIME_LANGUAGE_MODEL_DICT_CONTENT_H

#include <cstdio>

#include "defines.h"
#include "suggest/policyimpl/dictionary/utils/trie_map.h"
#include "utils/byte_array_view.h"

namespace latinime {

class LanguageModelDictContent {
 public:
    LanguageModelDictContent(const ReadWriteByteArrayView trieMapBuffer,
            const bool hasHistoricalInfo)
            : mTrieMap(trieMapBuffer) {}

    explicit LanguageModelDictContent(const bool hasHistoricalInfo) : mTrieMap() {}

    bool save(FILE *const file) const;

 private:
    DISALLOW_COPY_AND_ASSIGN(LanguageModelDictContent);

    TrieMap mTrieMap;
};
} // namespace latinime
#endif /* LATINIME_LANGUAGE_MODEL_DICT_CONTENT_H */
