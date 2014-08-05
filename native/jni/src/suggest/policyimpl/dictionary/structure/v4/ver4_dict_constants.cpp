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

#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_constants.h"

namespace latinime {

const char *const Ver4DictConstants::BODY_FILE_EXTENSION = ".body";
const char *const Ver4DictConstants::HEADER_FILE_EXTENSION = ".header";

// Version 4 dictionary size is implicitly limited to 8MB due to 3-byte offsets.
const int Ver4DictConstants::MAX_DICTIONARY_SIZE = 8 * 1024 * 1024;
// Extended region size, which is not GCed region size in dict file + additional buffer size, is
// limited to 1MB to prevent from inefficient traversing.
const int Ver4DictConstants::MAX_DICT_EXTENDED_REGION_SIZE = 1 * 1024 * 1024;

// NUM_OF_BUFFERS_FOR_SINGLE_DICT_CONTENT for Trie and TerminalAddressLookupTable.
// NUM_OF_BUFFERS_FOR_LANGUAGE_MODEL_DICT_CONTENT for language model.
// NUM_OF_BUFFERS_FOR_SPARSE_TABLE_DICT_CONTENT for bigram and shortcut.
const size_t Ver4DictConstants::NUM_OF_CONTENT_BUFFERS_IN_BODY_FILE =
        NUM_OF_BUFFERS_FOR_SINGLE_DICT_CONTENT * 2
                + NUM_OF_BUFFERS_FOR_LANGUAGE_MODEL_DICT_CONTENT
                + NUM_OF_BUFFERS_FOR_SPARSE_TABLE_DICT_CONTENT * 2;
const int Ver4DictConstants::TRIE_BUFFER_INDEX = 0;
const int Ver4DictConstants::TERMINAL_ADDRESS_LOOKUP_TABLE_BUFFER_INDEX =
        TRIE_BUFFER_INDEX + NUM_OF_BUFFERS_FOR_SINGLE_DICT_CONTENT;
const int Ver4DictConstants::LANGUAGE_MODEL_BUFFER_INDEX =
        TERMINAL_ADDRESS_LOOKUP_TABLE_BUFFER_INDEX + NUM_OF_BUFFERS_FOR_SINGLE_DICT_CONTENT;
const int Ver4DictConstants::BIGRAM_BUFFERS_INDEX =
        LANGUAGE_MODEL_BUFFER_INDEX + NUM_OF_BUFFERS_FOR_LANGUAGE_MODEL_DICT_CONTENT;
const int Ver4DictConstants::SHORTCUT_BUFFERS_INDEX =
        BIGRAM_BUFFERS_INDEX + NUM_OF_BUFFERS_FOR_SPARSE_TABLE_DICT_CONTENT;

const int Ver4DictConstants::NOT_A_TERMINAL_ID = -1;
const int Ver4DictConstants::PROBABILITY_SIZE = 1;
const int Ver4DictConstants::FLAGS_IN_PROBABILITY_FILE_SIZE = 1;
const int Ver4DictConstants::TERMINAL_ADDRESS_TABLE_ADDRESS_SIZE = 3;
const int Ver4DictConstants::NOT_A_TERMINAL_ADDRESS = 0;
const int Ver4DictConstants::TERMINAL_ID_FIELD_SIZE = 4;
const int Ver4DictConstants::TIME_STAMP_FIELD_SIZE = 4;
const int Ver4DictConstants::WORD_LEVEL_FIELD_SIZE = 1;
const int Ver4DictConstants::WORD_COUNT_FIELD_SIZE = 1;

const int Ver4DictConstants::BIGRAM_ADDRESS_TABLE_BLOCK_SIZE = 16;
const int Ver4DictConstants::BIGRAM_ADDRESS_TABLE_DATA_SIZE = 4;
const int Ver4DictConstants::SHORTCUT_ADDRESS_TABLE_BLOCK_SIZE = 64;
const int Ver4DictConstants::SHORTCUT_ADDRESS_TABLE_DATA_SIZE = 4;

const int Ver4DictConstants::BIGRAM_TARGET_TERMINAL_ID_FIELD_SIZE = 3;
// Unsigned int max value of BIGRAM_TARGET_TERMINAL_ID_FIELD_SIZE-byte is used for representing
// invalid terminal ID in bigram lists.
const int Ver4DictConstants::INVALID_BIGRAM_TARGET_TERMINAL_ID =
        (1 << (BIGRAM_TARGET_TERMINAL_ID_FIELD_SIZE * 8)) - 1;
const int Ver4DictConstants::BIGRAM_FLAGS_FIELD_SIZE = 1;
const int Ver4DictConstants::BIGRAM_PROBABILITY_MASK = 0x0F;
const int Ver4DictConstants::BIGRAM_IS_LINK_MASK = 0x80;
const int Ver4DictConstants::BIGRAM_LARGE_PROBABILITY_FIELD_SIZE = 1;

const int Ver4DictConstants::SHORTCUT_FLAGS_FIELD_SIZE = 1;
const int Ver4DictConstants::SHORTCUT_PROBABILITY_MASK = 0x0F;
const int Ver4DictConstants::SHORTCUT_HAS_NEXT_MASK = 0x80;

const size_t Ver4DictConstants::NUM_OF_BUFFERS_FOR_SINGLE_DICT_CONTENT = 1;
const size_t Ver4DictConstants::NUM_OF_BUFFERS_FOR_SPARSE_TABLE_DICT_CONTENT = 3;
const size_t Ver4DictConstants::NUM_OF_BUFFERS_FOR_LANGUAGE_MODEL_DICT_CONTENT = 1;

} // namespace latinime
