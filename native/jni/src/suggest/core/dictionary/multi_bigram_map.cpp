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

#include "suggest/core/dictionary/multi_bigram_map.h"

#include <cstddef>

namespace latinime {

// Max number of bigram maps (previous word contexts) to be cached. Increasing this number
// could improve bigram lookup speed for multi-word suggestions, but at the cost of more memory
// usage. Also, there are diminishing returns since the most frequently used bigrams are
// typically near the beginning of the input and are thus the first ones to be cached. Note
// that these bigrams are reset for each new composing word.
const size_t MultiBigramMap::MAX_CACHED_PREV_WORDS_IN_BIGRAM_MAP = 25;

// Most common previous word contexts currently have 100 bigrams
const int MultiBigramMap::BigramMap::DEFAULT_HASH_MAP_SIZE_FOR_EACH_BIGRAM_MAP = 100;

} // namespace latinime
