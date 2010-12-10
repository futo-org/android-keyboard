/*
**
** Copyright 2010, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#ifndef LATINIME_DEFINES_H
#define LATINIME_DEFINES_H

#ifdef FLAG_DBG
#include <cutils/log.h>
#ifndef LOG_TAG
#define LOG_TAG "LatinIME: "
#endif
#define DEBUG_DICT true
#define DEBUG_DICT_FULL true
#define DEBUG_SHOW_FOUND_WORD DEBUG_DICT_FULL
#define DEBUG_NODE DEBUG_DICT_FULL
#define DEBUG_TRACE DEBUG_DICT_FULL
#else // FLAG_DBG
#define LOGI
#define DEBUG_DICT false
#define DEBUG_DICT_FULL false
#define DEBUG_SHOW_FOUND_WORD false
#define DEBUG_NODE false
#define DEBUG_TRACE false
#endif // FLAG_DBG

#ifndef U_SHORT_MAX
#define U_SHORT_MAX 1 << 16
#endif

// 22-bit address = ~4MB dictionary size limit, which on average would be about 200k-300k words
#define ADDRESS_MASK 0x3FFFFF

// The bit that decides if an address follows in the next 22 bits
#define FLAG_ADDRESS_MASK 0x40
// The bit that decides if this is a terminal node for a word. The node could still have children,
// if the word has other endings.
#define FLAG_TERMINAL_MASK 0x80

#define FLAG_BIGRAM_READ 0x80
#define FLAG_BIGRAM_CHILDEXIST 0x40
#define FLAG_BIGRAM_CONTINUED 0x80
#define FLAG_BIGRAM_FREQ 0x7F

#define DICTIONARY_VERSION_MIN 200
#define DICTIONARY_HEADER_SIZE 2
#define NOT_VALID_WORD -99

#define SUGGEST_WORDS_WITH_MISSING_CHARACTER true
#define SUGGEST_WORDS_WITH_MISSING_SPACE_CHARACTER true
#define SUGGEST_WORDS_WITH_EXCESSIVE_CHARACTER true
#define SUGGEST_WORDS_WITH_TRANSPOSED_CHARACTERS true

#define WORDS_WITH_MISSING_CHARACTER_DEMOTION_RATE 75
#define WORDS_WITH_MISSING_SPACE_CHARACTER_DEMOTION_RATE 80
#define WORDS_WITH_EXCESSIVE_CHARACTER_DEMOTION_RATE 75
#define WORDS_WITH_TRANSPOSED_CHARACTERS_DEMOTION_RATE 60

// This should be greater than or equal to MAX_WORD_LENGTH defined in BinaryDictionary.java
// This is only used for the size of array. Not to be used in c functions.
#define MAX_WORD_LENGTH_INTERNAL 48

#define MAX_DEPTH_MULTIPLIER 3

#define MIN_SUGGEST_DEPTH 2

#define min(a,b) ((a)<(b)?(a):(b))

#endif // LATINIME_DEFINES_H
