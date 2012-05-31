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

#if defined(FLAG_DO_PROFILE) || defined(FLAG_DBG)
#include <cutils/log.h>
#define AKLOGE ALOGE
#define AKLOGI ALOGI

#define DUMP_WORD(word, length) do { dumpWord(word, length); } while(0)
#define DUMP_WORD_INT(word, length) do { dumpWordInt(word, length); } while(0)

static inline void dumpWord(const unsigned short* word, const int length) {
    static char charBuf[50];

    for (int i = 0; i < length; ++i) {
        charBuf[i] = word[i];
    }
    charBuf[length] = 0;
    AKLOGI("[ %s ]", charBuf);
}

static inline void dumpWordInt(const int* word, const int length) {
    static char charBuf[50];

    for (int i = 0; i < length; ++i) {
        charBuf[i] = word[i];
    }
    charBuf[length] = 0;
    AKLOGI("i[ %s ]", charBuf);
}

#else
#define AKLOGE(fmt, ...)
#define AKLOGI(fmt, ...)
#define DUMP_WORD(word, length)
#define DUMP_WORD_INT(word, length)
#endif

#ifdef FLAG_DO_PROFILE
// Profiler
#include <time.h>

#define PROF_BUF_SIZE 100
static float profile_buf[PROF_BUF_SIZE];
static float profile_old[PROF_BUF_SIZE];
static unsigned int profile_counter[PROF_BUF_SIZE];

#define PROF_RESET               prof_reset()
#define PROF_COUNT(prof_buf_id)  ++profile_counter[prof_buf_id]
#define PROF_OPEN                do { PROF_RESET; PROF_START(PROF_BUF_SIZE - 1); } while(0)
#define PROF_START(prof_buf_id)  do { \
        PROF_COUNT(prof_buf_id); profile_old[prof_buf_id] = (clock()); } while(0)
#define PROF_CLOSE               do { PROF_END(PROF_BUF_SIZE - 1); PROF_OUTALL; } while(0)
#define PROF_END(prof_buf_id)    profile_buf[prof_buf_id] += ((clock()) - profile_old[prof_buf_id])
#define PROF_CLOCKOUT(prof_buf_id) \
        AKLOGI("%s : clock is %f", __FUNCTION__, (clock() - profile_old[prof_buf_id]))
#define PROF_OUTALL              do { AKLOGI("--- %s ---", __FUNCTION__); prof_out(); } while(0)

static inline void prof_reset(void) {
    for (int i = 0; i < PROF_BUF_SIZE; ++i) {
        profile_buf[i] = 0;
        profile_old[i] = 0;
        profile_counter[i] = 0;
    }
}

static inline void prof_out(void) {
    if (profile_counter[PROF_BUF_SIZE - 1] != 1) {
        AKLOGI("Error: You must call PROF_OPEN before PROF_CLOSE.");
    }
    AKLOGI("Total time is %6.3f ms.",
            profile_buf[PROF_BUF_SIZE - 1] * 1000 / (float)CLOCKS_PER_SEC);
    float all = 0;
    for (int i = 0; i < PROF_BUF_SIZE - 1; ++i) {
        all += profile_buf[i];
    }
    if (all == 0) all = 1;
    for (int i = 0; i < PROF_BUF_SIZE - 1; ++i) {
        if (profile_buf[i] != 0) {
            AKLOGI("(%d): Used %4.2f%%, %8.4f ms. Called %d times.",
                    i, (profile_buf[i] * 100 / all),
                    profile_buf[i] * 1000 / (float)CLOCKS_PER_SEC, profile_counter[i]);
        }
    }
}

#else // FLAG_DO_PROFILE
#define PROF_BUF_SIZE 0
#define PROF_RESET
#define PROF_COUNT(prof_buf_id)
#define PROF_OPEN
#define PROF_START(prof_buf_id)
#define PROF_CLOSE
#define PROF_END(prof_buf_id)
#define PROF_CLOCK_OUT(prof_buf_id)
#define PROF_CLOCKOUT(prof_buf_id)
#define PROF_OUTALL

#endif // FLAG_DO_PROFILE

#ifdef FLAG_DBG
#include <cutils/log.h>
#ifndef LOG_TAG
#define LOG_TAG "LatinIME: "
#endif
#define DEBUG_DICT true
#define DEBUG_DICT_FULL false
#define DEBUG_EDIT_DISTANCE false
#define DEBUG_SHOW_FOUND_WORD false
#define DEBUG_NODE DEBUG_DICT_FULL
#define DEBUG_TRACE DEBUG_DICT_FULL
#define DEBUG_PROXIMITY_INFO false
#define DEBUG_PROXIMITY_CHARS false
#define DEBUG_CORRECTION false
#define DEBUG_CORRECTION_FREQ false
#define DEBUG_WORDS_PRIORITY_QUEUE false

#else // FLAG_DBG

#define DEBUG_DICT false
#define DEBUG_DICT_FULL false
#define DEBUG_EDIT_DISTANCE false
#define DEBUG_SHOW_FOUND_WORD false
#define DEBUG_NODE false
#define DEBUG_TRACE false
#define DEBUG_PROXIMITY_INFO false
#define DEBUG_PROXIMITY_CHARS false
#define DEBUG_CORRECTION false
#define DEBUG_CORRECTION_FREQ false
#define DEBUG_WORDS_PRIORITY_QUEUE false


#endif // FLAG_DBG

#ifndef U_SHORT_MAX
#define U_SHORT_MAX 65535    // ((1 << 16) - 1)
#endif
#ifndef S_INT_MAX
#define S_INT_MAX 2147483647 // ((1 << 31) - 1)
#endif

// Define this to use mmap() for dictionary loading.  Undefine to use malloc() instead of mmap().
// We measured and compared performance of both, and found mmap() is fairly good in terms of
// loading time, and acceptable even for several initial lookups which involve page faults.
#define USE_MMAP_FOR_DICTIONARY

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
#define NOT_VALID_WORD -99
#define NOT_A_CHARACTER -1
#define NOT_A_DISTANCE -1
#define NOT_A_COORDINATE -1
#define EQUIVALENT_CHAR_WITHOUT_DISTANCE_INFO -2
#define PROXIMITY_CHAR_WITHOUT_DISTANCE_INFO -3
#define ADDITIONAL_PROXIMITY_CHAR_DISTANCE_INFO -4
#define NOT_AN_INDEX -1
#define NOT_A_PROBABILITY -1

#define KEYCODE_SPACE ' '

#define CALIBRATE_SCORE_BY_TOUCH_COORDINATES true

#define SUGGEST_WORDS_WITH_MISSING_CHARACTER true
#define SUGGEST_WORDS_WITH_EXCESSIVE_CHARACTER true
#define SUGGEST_WORDS_WITH_TRANSPOSED_CHARACTERS true
#define SUGGEST_MULTIPLE_WORDS true

// The following "rate"s are used as a multiplier before dividing by 100, so they are in percent.
#define WORDS_WITH_MISSING_CHARACTER_DEMOTION_RATE 80
#define WORDS_WITH_MISSING_CHARACTER_DEMOTION_START_POS_10X 12
#define WORDS_WITH_MISSING_SPACE_CHARACTER_DEMOTION_RATE 58
#define WORDS_WITH_MISTYPED_SPACE_DEMOTION_RATE 50
#define WORDS_WITH_EXCESSIVE_CHARACTER_DEMOTION_RATE 75
#define WORDS_WITH_EXCESSIVE_CHARACTER_OUT_OF_PROXIMITY_DEMOTION_RATE 75
#define WORDS_WITH_TRANSPOSED_CHARACTERS_DEMOTION_RATE 70
#define FULL_MATCHED_WORDS_PROMOTION_RATE 120
#define WORDS_WITH_PROXIMITY_CHARACTER_DEMOTION_RATE 90
#define WORDS_WITH_ADDITIONAL_PROXIMITY_CHARACTER_DEMOTION_RATE 70
#define WORDS_WITH_MATCH_SKIP_PROMOTION_RATE 105
#define WORDS_WITH_JUST_ONE_CORRECTION_PROMOTION_RATE 148
#define WORDS_WITH_JUST_ONE_CORRECTION_PROMOTION_MULTIPLIER 3
#define CORRECTION_COUNT_RATE_DEMOTION_RATE_BASE 45
#define INPUT_EXCEEDS_OUTPUT_DEMOTION_RATE 70
#define FIRST_CHAR_DIFFERENT_DEMOTION_RATE 96
#define TWO_WORDS_CAPITALIZED_DEMOTION_RATE 50
#define TWO_WORDS_CORRECTION_DEMOTION_BASE 80
#define TWO_WORDS_PLUS_OTHER_ERROR_CORRECTION_DEMOTION_DIVIDER 1
#define ZERO_DISTANCE_PROMOTION_RATE 110
#define NEUTRAL_SCORE_SQUARED_RADIUS 8.0f
#define HALF_SCORE_SQUARED_RADIUS 32.0f
#define MAX_FREQ 255
#define MAX_BIGRAM_FREQ 15

// This must be greater than or equal to MAX_WORD_LENGTH defined in BinaryDictionary.java
// This is only used for the size of array. Not to be used in c functions.
#define MAX_WORD_LENGTH_INTERNAL 48

// This must be equal to ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE in KeyDetector.java
#define ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE 2

// Word limit for sub queues used in WordsPriorityQueuePool.  Sub queues are temporary queues used
// for better performance.
// Holds up to 1 candidate for each word
#define SUB_QUEUE_MAX_WORDS 1
#define SUB_QUEUE_MAX_COUNT 10
#define SUB_QUEUE_MIN_WORD_LENGTH 4
// TODO: Extend this limitation
#define MULTIPLE_WORDS_SUGGESTION_MAX_WORDS 5
// TODO: Remove this limitation
#define MULTIPLE_WORDS_SUGGESTION_MAX_WORD_LENGTH 12
// TODO: Remove this limitation
#define MULTIPLE_WORDS_SUGGESTION_MAX_TOTAL_TRAVERSE_COUNT 45
#define MULTIPLE_WORDS_DEMOTION_RATE 80
#define MIN_INPUT_LENGTH_FOR_THREE_OR_MORE_WORDS_CORRECTION 6

#define TWO_WORDS_CORRECTION_WITH_OTHER_ERROR_THRESHOLD 0.35
#define START_TWO_WORDS_CORRECTION_THRESHOLD 0.185
/* heuristic... This should be changed if we change the unit of the frequency. */
#define SUPPRESS_SHORT_MULTIPLE_WORDS_THRESHOLD_FREQ (MAX_FREQ * 58 / 100)

#define MAX_DEPTH_MULTIPLIER 3

#define FIRST_WORD_INDEX 0

// TODO: Reduce this constant if possible; check the maximum number of digraphs in the same
// word in the dictionary for languages with digraphs, like German and French
#define DEFAULT_MAX_DIGRAPH_SEARCH_DEPTH 5

// Minimum suggest depth for one word for all cases except for missing space suggestions.
#define MIN_SUGGEST_DEPTH 1
#define MIN_USER_TYPED_LENGTH_FOR_MULTIPLE_WORD_SUGGESTION 3
#define MIN_USER_TYPED_LENGTH_FOR_EXCESSIVE_CHARACTER_SUGGESTION 3

// Size, in bytes, of the bloom filter index for bigrams
// 128 gives us 1024 buckets. The probability of false positive is (1 - e ** (-kn/m))**k,
// where k is the number of hash functions, n the number of bigrams, and m the number of
// bits we can test.
// At the moment 100 is the maximum number of bigrams for a word with the current
// dictionaries, so n = 100. 1024 buckets give us m = 1024.
// With 1 hash function, our false positive rate is about 9.3%, which should be enough for
// our uses since we are only using this to increase average performance. For the record,
// k = 2 gives 3.1% and k = 3 gives 1.6%. With k = 1, making m = 2048 gives 4.8%,
// and m = 4096 gives 2.4%.
#define BIGRAM_FILTER_BYTE_SIZE 128
// Must be smaller than BIGRAM_FILTER_BYTE_SIZE * 8, and preferably prime. 1021 is the largest
// prime under 128 * 8.
#define BIGRAM_FILTER_MODULO 1021
#if BIGRAM_FILTER_BYTE_SIZE * 8 < BIGRAM_FILTER_MODULO
#error "BIGRAM_FILTER_MODULO is larger than BIGRAM_FILTER_BYTE_SIZE"
#endif

template<typename T> inline T min(T a, T b) { return a < b ? a : b; }
template<typename T> inline T max(T a, T b) { return a > b ? a : b; }

// The ratio of neutral area radius to sweet spot radius.
#define NEUTRAL_AREA_RADIUS_RATIO 1.3f

// DEBUG
#define INPUTLENGTH_FOR_DEBUG -1
#define MIN_OUTPUT_INDEX_FOR_DEBUG -1

#endif // LATINIME_DEFINES_H
