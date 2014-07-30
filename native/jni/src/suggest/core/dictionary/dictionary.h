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

#include <memory>

#include "defines.h"
#include "jni.h"
#include "suggest/core/dictionary/ngram_listener.h"
#include "suggest/core/dictionary/property/word_property.h"
#include "suggest/core/policy/dictionary_header_structure_policy.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "suggest/core/suggest_interface.h"

namespace latinime {

class DictionaryStructureWithBufferPolicy;
class DicTraverseSession;
class PrevWordsInfo;
class ProximityInfo;
class SuggestionResults;
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
    static const int KIND_FLAG_EXACT_MATCH_WITH_INTENTIONAL_OMISSION = 0x20000000;

    Dictionary(JNIEnv *env, DictionaryStructureWithBufferPolicy::StructurePolicyPtr
            dictionaryStructureWithBufferPolicy);

    void getSuggestions(ProximityInfo *proximityInfo, DicTraverseSession *traverseSession,
            int *xcoordinates, int *ycoordinates, int *times, int *pointerIds, int *inputCodePoints,
            int inputSize, const PrevWordsInfo *const prevWordsInfo,
            const SuggestOptions *const suggestOptions, const float languageWeight,
            SuggestionResults *const outSuggestionResults) const;

    void getPredictions(const PrevWordsInfo *const prevWordsInfo,
            SuggestionResults *const outSuggestionResults) const;

    int getProbability(const int *word, int length) const;

    int getMaxProbabilityOfExactMatches(const int *word, int length) const;

    int getNgramProbability(const PrevWordsInfo *const prevWordsInfo,
            const int *word, int length) const;

    bool addUnigramEntry(const int *const codePoints, const int codePointCount,
            const UnigramProperty *const unigramProperty);

    bool removeUnigramEntry(const int *const codePoints, const int codePointCount);

    bool addNgramEntry(const PrevWordsInfo *const prevWordsInfo,
            const BigramProperty *const bigramProperty);

    bool removeNgramEntry(const PrevWordsInfo *const prevWordsInfo, const int *const word,
            const int length);

    bool flush(const char *const filePath);

    bool flushWithGC(const char *const filePath);

    bool needsToRunGC(const bool mindsBlockByGC);

    void getProperty(const char *const query, const int queryLength, char *const outResult,
            const int maxResultLength);

    const WordProperty getWordProperty(const int *const codePoints, const int codePointCount);

    // Method to iterate all words in the dictionary.
    // The returned token has to be used to get the next word. If token is 0, this method newly
    // starts iterating the dictionary.
    int getNextWordAndNextToken(const int token, int *const outCodePoints,
            int *const outCodePointCount);

    const DictionaryStructureWithBufferPolicy *getDictionaryStructurePolicy() const {
        return mDictionaryStructureWithBufferPolicy.get();
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(Dictionary);

    typedef std::unique_ptr<SuggestInterface> SuggestInterfacePtr;

    class NgramListenerForPrediction : public NgramListener {
     public:
        NgramListenerForPrediction(const PrevWordsInfo *const prevWordsInfo,
                SuggestionResults *const suggestionResults,
                const DictionaryStructureWithBufferPolicy *const dictStructurePolicy);
        virtual void onVisitEntry(const int ngramProbability, const int targetPtNodePos);

     private:
        DISALLOW_IMPLICIT_CONSTRUCTORS(NgramListenerForPrediction);

        const PrevWordsInfo *const mPrevWordsInfo;
        SuggestionResults *const mSuggestionResults;
        const DictionaryStructureWithBufferPolicy *const mDictStructurePolicy;
    };

    static const int HEADER_ATTRIBUTE_BUFFER_SIZE;

    const DictionaryStructureWithBufferPolicy::StructurePolicyPtr
            mDictionaryStructureWithBufferPolicy;
    const SuggestInterfacePtr mGestureSuggest;
    const SuggestInterfacePtr mTypingSuggest;

    void logDictionaryInfo(JNIEnv *const env) const;
};
} // namespace latinime
#endif // LATINIME_DICTIONARY_H
