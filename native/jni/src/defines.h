/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef LATINIME_DEFINES_H
#define LATINIME_DEFINES_H

#ifdef __GNUC__
#define AK_FORCE_INLINE __attribute__((always_inline)) __inline__
#else // __GNUC__
#define AK_FORCE_INLINE inline
#endif // __GNUC__

#if defined(FLAG_DO_PROFILE) || defined(FLAG_DBG)
#undef AK_FORCE_INLINE
#define AK_FORCE_INLINE inline
#endif // defined(FLAG_DO_PROFILE) || defined(FLAG_DBG)

// Must be equal to Constants.Dictionary.MAX_WORD_LENGTH in Java
#define MAX_WORD_LENGTH 48
// Must be equal to BinaryDictionary.MAX_RESULTS in Java
#define MAX_RESULTS 18
// Must be equal to ProximityInfo.MAX_PROXIMITY_CHARS_SIZE in Java
#define MAX_PROXIMITY_CHARS_SIZE 16
#define ADDITIONAL_PROXIMITY_CHAR_DELIMITER_CODE 2
#define NELEMS(x) (sizeof(x) / sizeof((x)[0]))

AK_FORCE_INLINE static int intArrayToCharArray(const int *const source, const int sourceSize,
        char *dest, const int destSize) {
    // We want to always terminate with a 0 char, so stop one short of the length to make
    // sure there is room.
    const int destLimit = destSize - 1;
    int si = 0;
    int di = 0;
    while (si < sourceSize && di < destLimit && 0 != source[si]) {
        const int codePoint = source[si++];
        if (codePoint < 0x7F) { // One byte
            dest[di++] = codePoint;
        } else if (codePoint < 0x7FF) { // Two bytes
            if (di + 1 >= destLimit) break;
            dest[di++] = 0xC0 + (codePoint >> 6);
            dest[di++] = 0x80 + (codePoint & 0x3F);
        } else if (codePoint < 0xFFFF) { // Three bytes
            if (di + 2 >= destLimit) break;
            dest[di++] = 0xE0 + (codePoint >> 12);
            dest[di++] = 0x80 + ((codePoint >> 6) & 0x3F);
            dest[di++] = 0x80 + (codePoint & 0x3F);
        } else if (codePoint <= 0x1FFFFF) { // Four bytes
            if (di + 3 >= destLimit) break;
            dest[di++] = 0xF0 + (codePoint >> 18);
            dest[di++] = 0x80 + ((codePoint >> 12) & 0x3F);
            dest[di++] = 0x80 + ((codePoint >> 6) & 0x3F);
            dest[di++] = 0x80 + (codePoint & 0x3F);
        } else if (codePoint <= 0x3FFFFFF) { // Five bytes
            if (di + 4 >= destLimit) break;
            dest[di++] = 0xF8 + (codePoint >> 24);
            dest[di++] = 0x80 + ((codePoint >> 18) & 0x3F);
            dest[di++] = 0x80 + ((codePoint >> 12) & 0x3F);
            dest[di++] = 0x80 + ((codePoint >> 6) & 0x3F);
            dest[di++] = codePoint & 0x3F;
        } else if (codePoint <= 0x7FFFFFFF) { // Six bytes
            if (di + 5 >= destLimit) break;
            dest[di++] = 0xFC + (codePoint >> 30);
            dest[di++] = 0x80 + ((codePoint >> 24) & 0x3F);
            dest[di++] = 0x80 + ((codePoint >> 18) & 0x3F);
            dest[di++] = 0x80 + ((codePoint >> 12) & 0x3F);
            dest[di++] = 0x80 + ((codePoint >> 6) & 0x3F);
            dest[di++] = codePoint & 0x3F;
        } else {
            // Not a code point... skip.
        }
    }
    dest[di] = 0;
    return di;
}

#if defined(FLAG_DO_PROFILE) || defined(FLAG_DBG)
#include <android/log.h>
#ifndef LOG_TAG
#define LOG_TAG "LatinIME: "
#endif // LOG_TAG
#define AKLOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, ##__VA_ARGS__)
#define AKLOGI(fmt, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, fmt, ##__VA_ARGS__)

#define DUMP_RESULT(words, frequencies) do { dumpResult(words, frequencies); } while (0)
#define DUMP_WORD(word, length) do { dumpWord(word, length); } while (0)
#define INTS_TO_CHARS(input, length, output, outlength) do { \
        intArrayToCharArray(input, length, output, outlength); } while (0)

static inline void dumpWordInfo(const int *word, const int length, const int rank,
        const int probability) {
    static char charBuf[50];
    const int N = intArrayToCharArray(word, length, charBuf, NELEMS(charBuf));
    if (N > 1) {
        AKLOGI("%2d [ %s ] (%d)", rank, charBuf, probability);
    }
}

static inline void dumpResult(const int *outWords, const int *frequencies) {
    AKLOGI("--- DUMP RESULT ---------");
    for (int i = 0; i < MAX_RESULTS; ++i) {
        dumpWordInfo(&outWords[i * MAX_WORD_LENGTH], MAX_WORD_LENGTH, i, frequencies[i]);
    }
    AKLOGI("-------------------------");
}

static AK_FORCE_INLINE void dumpWord(const int *word, const int length) {
    static char charBuf[50];
    const int N = intArrayToCharArray(word, length, charBuf, NELEMS(charBuf));
    if (N > 1) {
        AKLOGI("[ %s ]", charBuf);
    }
}

#ifndef __ANDROID__
#include <cassert>
#include <execinfo.h>
#include <stdlib.h>

#define DO_ASSERT_TEST
#define ASSERT(success) do { if (!(success)) { showStackTrace(); assert(success);} } while (0)
#define SHOW_STACK_TRACE do { showStackTrace(); } while (0)

static inline void showStackTrace() {
    void *callstack[128];
    int i, frames = backtrace(callstack, 128);
    char **strs = backtrace_symbols(callstack, frames);
    for (i = 0; i < frames; ++i) {
        if (i == 0) {
            AKLOGI("=== Trace ===");
            continue;
        }
        AKLOGI("%s", strs[i]);
    }
    free(strs);
}
#else // __ANDROID__
#include <cassert>
#define DO_ASSERT_TEST
#define ASSERT(success) assert(success)
#define SHOW_STACK_TRACE
#endif // __ANDROID__

#else // defined(FLAG_DO_PROFILE) || defined(FLAG_DBG)
#define AKLOGE(fmt, ...)
#define AKLOGI(fmt, ...)
#define DUMP_RESULT(words, frequencies)
#define DUMP_WORD(word, length)
#undef DO_ASSERT_TEST
#define ASSERT(success)
#define SHOW_STACK_TRACE
#define INTS_TO_CHARS(input, length, output)
#endif // defined(FLAG_DO_PROFILE) || defined(FLAG_DBG)

#ifdef FLAG_DO_PROFILE
// Profiler
#include <time.h>

#define PROF_BUF_SIZE 100
static float profile_buf[PROF_BUF_SIZE];
static float profile_old[PROF_BUF_SIZE];
static unsigned int profile_counter[PROF_BUF_SIZE];

#define PROF_RESET               prof_reset()
#define PROF_COUNT(prof_buf_id)  ++profile_counter[prof_buf_id]
#define PROF_OPEN                do { PROF_RESET; PROF_START(PROF_BUF_SIZE - 1); } while (0)
#define PROF_START(prof_buf_id)  do { \
        PROF_COUNT(prof_buf_id); profile_old[prof_buf_id] = (clock()); } while (0)
#define PROF_CLOSE               do { PROF_END(PROF_BUF_SIZE - 1); PROF_OUTALL; } while (0)
#define PROF_END(prof_buf_id)    profile_buf[prof_buf_id] += ((clock()) - profile_old[prof_buf_id])
#define PROF_CLOCKOUT(prof_buf_id) \
        AKLOGI("%s : clock is %f", __FUNCTION__, (clock() - profile_old[prof_buf_id]))
#define PROF_OUTALL              do { AKLOGI("--- %s ---", __FUNCTION__); prof_out(); } while (0)

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
            profile_buf[PROF_BUF_SIZE - 1] * 1000.0f / static_cast<float>(CLOCKS_PER_SEC));
    float all = 0.0f;
    for (int i = 0; i < PROF_BUF_SIZE - 1; ++i) {
        all += profile_buf[i];
    }
    if (all < 1.0f) all = 1.0f;
    for (int i = 0; i < PROF_BUF_SIZE - 1; ++i) {
        if (profile_buf[i] > 0.0f) {
            AKLOGI("(%d): Used %4.2f%%, %8.4f ms. Called %d times.",
                    i, (profile_buf[i] * 100.0f / all),
                    profile_buf[i] * 1000.0f / static_cast<float>(CLOCKS_PER_SEC),
                    profile_counter[i]);
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
#define DEBUG_DICT true
#define DEBUG_DICT_FULL false
#define DEBUG_EDIT_DISTANCE false
#define DEBUG_NODE DEBUG_DICT_FULL
#define DEBUG_TRACE DEBUG_DICT_FULL
#define DEBUG_PROXIMITY_INFO false
#define DEBUG_PROXIMITY_CHARS false
#define DEBUG_CORRECTION false
#define DEBUG_CORRECTION_FREQ false
#define DEBUG_SAMPLING_POINTS false
#define DEBUG_POINTS_PROBABILITY false
#define DEBUG_DOUBLE_LETTER false
#define DEBUG_CACHE false
#define DEBUG_DUMP_ERROR false
#define DEBUG_EVALUATE_MOST_PROBABLE_STRING false

#ifdef FLAG_FULL_DBG
#define DEBUG_GEO_FULL true
#else
#define DEBUG_GEO_FULL false
#endif

#else // FLAG_DBG

#define DEBUG_DICT false
#define DEBUG_DICT_FULL false
#define DEBUG_EDIT_DISTANCE false
#define DEBUG_NODE false
#define DEBUG_TRACE false
#define DEBUG_PROXIMITY_INFO false
#define DEBUG_PROXIMITY_CHARS false
#define DEBUG_CORRECTION false
#define DEBUG_CORRECTION_FREQ false
#define DEBUG_SAMPLING_POINTS false
#define DEBUG_POINTS_PROBABILITY false
#define DEBUG_DOUBLE_LETTER false
#define DEBUG_CACHE false
#define DEBUG_DUMP_ERROR false
#define DEBUG_EVALUATE_MOST_PROBABLE_STRING false

#define DEBUG_GEO_FULL false

#endif // FLAG_DBG

#ifndef S_INT_MAX
#define S_INT_MAX 2147483647 // ((1 << 31) - 1)
#endif
#ifndef S_INT_MIN
// The literal constant -2147483648 does not work in C prior C90, because
// the compiler tries to fit the positive number into an int and then negate it.
// GCC warns about this.
#define S_INT_MIN (-2147483647 - 1) // -(1 << 31)
#endif

#define M_PI_F 3.14159265f
#define MAX_PERCENTILE 100

// Number of base-10 digits in the largest integer + 1 to leave room for a zero terminator.
// As such, this is the maximum number of characters will be needed to represent an int as a
// string, including the terminator; this is used as the size of a string buffer large enough to
// hold any value that is intended to fit in an integer, e.g. in the code that reads the header
// of the binary dictionary where a {key,value} string pair scheme is used.
#define LARGEST_INT_DIGIT_COUNT 11

#define NOT_A_CODE_POINT (-1)
#define NOT_A_DISTANCE (-1)
#define NOT_A_COORDINATE (-1)
#define NOT_AN_INDEX (-1)
#define NOT_A_PROBABILITY (-1)
#define NOT_A_DICT_POS (S_INT_MIN)

// A special value to mean the first word confidence makes no sense in this case,
// e.g. this is not a multi-word suggestion.
#define NOT_A_FIRST_WORD_CONFIDENCE (S_INT_MAX)
// How high the confidence needs to be for us to auto-commit. Arbitrary.
// This needs to be the same as CONFIDENCE_FOR_AUTO_COMMIT in BinaryDictionary.java
#define CONFIDENCE_FOR_AUTO_COMMIT (1000000)
// 80% of the full confidence
#define DISTANCE_WEIGHT_FOR_AUTO_COMMIT (80 * CONFIDENCE_FOR_AUTO_COMMIT / 100)
// 100% of the full confidence
#define LENGTH_WEIGHT_FOR_AUTO_COMMIT (CONFIDENCE_FOR_AUTO_COMMIT)
// 80% of the full confidence
#define SPACE_COUNT_WEIGHT_FOR_AUTO_COMMIT (80 * CONFIDENCE_FOR_AUTO_COMMIT / 100)

#define KEYCODE_SPACE ' '
#define KEYCODE_SINGLE_QUOTE '\''
#define KEYCODE_HYPHEN_MINUS '-'

#define SUGGEST_INTERFACE_OUTPUT_SCALE 1000000.0f
#define MAX_PROBABILITY 255
#define MAX_BIGRAM_ENCODED_PROBABILITY 15

// Assuming locale strings such as en_US, sr-Latn etc.
#define MAX_LOCALE_STRING_LENGTH 10

// Max value for length, distance and probability which are used in weighting
// TODO: Remove
#define MAX_VALUE_FOR_WEIGHTING 10000000

// The max number of the keys in one keyboard layout
#define MAX_KEY_COUNT_IN_A_KEYBOARD 64

// TODO: Remove
#define MAX_POINTER_COUNT 1
#define MAX_POINTER_COUNT_G 2

template<typename T> AK_FORCE_INLINE const T &min(const T &a, const T &b) { return a < b ? a : b; }
template<typename T> AK_FORCE_INLINE const T &max(const T &a, const T &b) { return a > b ? a : b; }

// DEBUG
#define INPUTLENGTH_FOR_DEBUG (-1)
#define MIN_OUTPUT_INDEX_FOR_DEBUG (-1)

#define DISALLOW_COPY_AND_ASSIGN(TypeName) \
  TypeName(const TypeName&);               \
  void operator=(const TypeName&)

#define DISALLOW_IMPLICIT_CONSTRUCTORS(TypeName) \
  TypeName();                                    \
  DISALLOW_COPY_AND_ASSIGN(TypeName)

// Used as a return value for character comparison
typedef enum {
    // Same char, possibly with different case or accent
    MATCH_CHAR,
    // It is a char located nearby on the keyboard
    PROXIMITY_CHAR,
    // Additional proximity char which can differ by language.
    ADDITIONAL_PROXIMITY_CHAR,
    // It is a substitution char
    SUBSTITUTION_CHAR,
    // It is an unrelated char
    UNRELATED_CHAR,
} ProximityType;

typedef enum {
    NOT_A_DOUBLE_LETTER,
    A_DOUBLE_LETTER,
    A_STRONG_DOUBLE_LETTER
} DoubleLetterLevel;

typedef enum {
    // Correction for MATCH_CHAR
    CT_MATCH,
    // Correction for PROXIMITY_CHAR
    CT_PROXIMITY,
    // Correction for ADDITIONAL_PROXIMITY_CHAR
    CT_ADDITIONAL_PROXIMITY,
    // Correction for SUBSTITUTION_CHAR
    CT_SUBSTITUTION,
    // Skip one omitted letter
    CT_OMISSION,
    // Delete an unnecessarily inserted letter
    CT_INSERTION,
    // Swap the order of next two touch points
    CT_TRANSPOSITION,
    CT_COMPLETION,
    CT_TERMINAL,
    CT_TERMINAL_INSERTION,
    // Create new word with space omission
    CT_NEW_WORD_SPACE_OMISSION,
    // Create new word with space substitution
    CT_NEW_WORD_SPACE_SUBSTITUTION,
} CorrectionType;

// ErrorType is mainly decided by CorrectionType but it is also depending on if
// the correction has really been performed or not.
typedef enum {
    // Substitution, omission and transposition
    ET_EDIT_CORRECTION,
    // Proximity error
    ET_PROXIMITY_CORRECTION,
    // Completion
    ET_COMPLETION,
    // New word
    // TODO: Remove.
    // A new word error should be an edit correction error or a proximity correction error.
    ET_NEW_WORD,
    // Treat error as an intentional omission when the CorrectionType is omission and the node can
    // be intentional omission.
    ET_INTENTIONAL_OMISSION,
    // Not treated as an error. Tracked for checking exact match
    ET_NOT_AN_ERROR
} ErrorType;
#endif // LATINIME_DEFINES_H
