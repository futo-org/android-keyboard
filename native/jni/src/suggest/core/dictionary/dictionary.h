/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef LATINIME_DICTIONARY_H
#define LATINIME_DICTIONARY_H

#include <stdint.h>

#include "defines.h"
#include "jni.h"

namespace latinime {

class BigramDictionary;
class DictionaryStructureWithBufferPolicy;
class DicTraverseSession;
class ProximityInfo;
class SuggestInterface;
class SuggestOptions;

class Dictionary {
 public:
    // Taken from SuggestedWords.java
    static const int KIND_MASK_KIND = 0xFF; // Mask to get only the kind
    static const int KIND_TYPED = 0; // What user typed
    static const int KIND_CORRECTION = 1; // Simple correction/suggestion
    static const int KIND_COMPLETION = 2; // Completion (suggestion with appended chars)
    static const int KIND_WHITELIST = 3; // Whitelisted word
    static const int KIND_BLACKLIST = 4; // Blacklisted word
    static const int KIND_HARDCODED = 5; // Hardcoded suggestion, e.g. punctuation
    static const int KIND_APP_DEFINED = 6; // Suggested by the application
    static const int KIND_SHORTCUT = 7; // A shortcut
    static const int KIND_PREDICTION = 8; // A prediction (== a suggestion with no input)
    // KIND_RESUMED: A resumed suggestion (comes from a span, currently this type is used only
    // in java for re-correction)
    static const int KIND_RESUMED = 9;
    static const int KIND_OOV_CORRECTION = 10; // Most probable string correction

    static const int KIND_MASK_FLAGS = 0xFFFFFF00; // Mask to get the flags
    static const int KIND_FLAG_POSSIBLY_OFFENSIVE = 0x80000000;
    static const int KIND_FLAG_EXACT_MATCH = 0x40000000;

    Dictionary(JNIEnv *env,
            DictionaryStructureWithBufferPolicy *const dictionaryStructureWithBufferPoilcy);

    int getSuggestions(ProximityInfo *proximityInfo, DicTraverseSession *traverseSession,
            int *xcoordinates, int *ycoordinates, int *times, int *pointerIds, int *inputCodePoints,
            int inputSize, int *prevWordCodePoints, int prevWordLength, int commitPoint,
            const SuggestOptions *const suggestOptions, int *outWords, int *frequencies,
            int *spaceIndices, int *outputTypes, int *outputAutoCommitFirstWordConfidence) const;

    int getBigrams(const int *word, int length, int *outWords, int *frequencies,
            int *outputTypes) const;

    int getProbability(const int *word, int length) const;

    int getBigramProbability(const int *word0, int length0, const int *word1, int length1) const;

    void addUnigramWord(const int *const word, const int length, const int probability);

    void addBigramWords(const int *const word0, const int length0, const int *const word1,
            const int length1, const int probability);

    void removeBigramWords(const int *const word0, const int length0, const int *const word1,
            const int length1);

    void flush(const char *const filePath);

    void flushWithGC(const char *const filePath);

    bool needsToRunGC(const bool mindsBlockByGC);

    void getProperty(const char *const query, char *const outResult,
            const int maxResultLength);

    const DictionaryStructureWithBufferPolicy *getDictionaryStructurePolicy() const {
        return mDictionaryStructureWithBufferPolicy;
    }

    virtual ~Dictionary();

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(Dictionary);

    static const int HEADER_ATTRIBUTE_BUFFER_SIZE;

    DictionaryStructureWithBufferPolicy *const mDictionaryStructureWithBufferPolicy;
    const BigramDictionary *const mBigramDictionary;
    const SuggestInterface *const mGestureSuggest;
    const SuggestInterface *const mTypingSuggest;

    void logDictionaryInfo(JNIEnv *const env) const;
};
} // namespace latinime
#endif // LATINIME_DICTIONARY_H
