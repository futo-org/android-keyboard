/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef LATINIME_WORDS_PRIORITY_QUEUE_H
#define LATINIME_WORDS_PRIORITY_QUEUE_H

#include <cstring> // for memcpy()
#include <queue>

#include "correction.h"
#include "defines.h"

namespace latinime {

class WordsPriorityQueue {
 public:
    struct SuggestedWord {
        int mScore;
        int mWord[MAX_WORD_LENGTH];
        int mWordLength;
        bool mUsed;
        int mType;

        void setParams(int score, int *word, int wordLength, int type) {
            mScore = score;
            mWordLength = wordLength;
            memcpy(mWord, word, sizeof(mWord[0]) * wordLength);
            mUsed = true;
            mType = type;
        }
    };

    WordsPriorityQueue(int maxWords)
            : mSuggestions(), MAX_WORDS(maxWords),
              mSuggestedWords(new SuggestedWord[MAX_WORD_LENGTH]), mHighestSuggestedWord(0) {
        for (int i = 0; i < MAX_WORD_LENGTH; ++i) {
            mSuggestedWords[i].mUsed = false;
        }
    }

    // Non virtual inline destructor -- never inherit this class
    AK_FORCE_INLINE ~WordsPriorityQueue() {
        delete[] mSuggestedWords;
    }

    void push(int score, int *word, int wordLength, int type) {
        SuggestedWord *sw = 0;
        if (size() >= MAX_WORDS) {
            sw = mSuggestions.top();
            const int minScore = sw->mScore;
            if (minScore >= score) {
                return;
            }
            sw->mUsed = false;
            mSuggestions.pop();
        }
        if (sw == 0) {
            sw = getFreeSuggestedWord(score, word, wordLength, type);
        } else {
            sw->setParams(score, word, wordLength, type);
        }
        if (sw == 0) {
            AKLOGE("SuggestedWord is accidentally null.");
            return;
        }
        if (DEBUG_WORDS_PRIORITY_QUEUE) {
            AKLOGI("Push word. %d, %d", score, wordLength);
            DUMP_WORD(word, wordLength);
        }
        mSuggestions.push(sw);
        if (!mHighestSuggestedWord || mHighestSuggestedWord->mScore < sw->mScore) {
            mHighestSuggestedWord = sw;
        }
    }

    SuggestedWord *top() const {
        if (mSuggestions.empty()) return 0;
        SuggestedWord *sw = mSuggestions.top();
        return sw;
    }

    int size() const {
        return static_cast<int>(mSuggestions.size());
    }

    AK_FORCE_INLINE void clear() {
        mHighestSuggestedWord = 0;
        while (!mSuggestions.empty()) {
            SuggestedWord *sw = mSuggestions.top();
            if (DEBUG_WORDS_PRIORITY_QUEUE) {
                AKLOGI("Clear word. %d", sw->mScore);
                DUMP_WORD(sw->mWord, sw->mWordLength);
            }
            sw->mUsed = false;
            mSuggestions.pop();
        }
    }

    AK_FORCE_INLINE void dumpTopWord() const {
        if (size() <= 0) {
            return;
        }
        DUMP_WORD(mHighestSuggestedWord->mWord, mHighestSuggestedWord->mWordLength);
    }

    AK_FORCE_INLINE float getHighestNormalizedScore(const int *before, const int beforeLength,
            int **outWord, int *outScore, int *outLength) const {
        if (!mHighestSuggestedWord) {
            return 0.0f;
        }
        return getNormalizedScore(mHighestSuggestedWord, before, beforeLength, outWord, outScore,
                outLength);
    }

    int outputSuggestions(const int *before, const int beforeLength, int *frequencies,
            int *outputCodePoints, int* outputTypes);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(WordsPriorityQueue);
    struct wordComparator {
        bool operator ()(SuggestedWord * left, SuggestedWord * right) {
            return left->mScore > right->mScore;
        }
    };

    SuggestedWord *getFreeSuggestedWord(int score, int *word, int wordLength, int type) const {
        for (int i = 0; i < MAX_WORD_LENGTH; ++i) {
            if (!mSuggestedWords[i].mUsed) {
                mSuggestedWords[i].setParams(score, word, wordLength, type);
                return &mSuggestedWords[i];
            }
        }
        return 0;
    }

    static float getNormalizedScore(SuggestedWord *sw, const int *before, const int beforeLength,
            int **outWord, int *outScore, int *outLength) {
        const int score = sw->mScore;
        int *word = sw->mWord;
        const int wordLength = sw->mWordLength;
        if (outScore) {
            *outScore = score;
        }
        if (outWord) {
            *outWord = word;
        }
        if (outLength) {
            *outLength = wordLength;
        }
        return Correction::RankingAlgorithm::calcNormalizedScore(before, beforeLength, word,
                wordLength, score);
    }

    typedef std::priority_queue<SuggestedWord *, std::vector<SuggestedWord *>,
            wordComparator> Suggestions;
    Suggestions mSuggestions;
    const int MAX_WORDS;
    SuggestedWord *mSuggestedWords;
    SuggestedWord *mHighestSuggestedWord;
};
} // namespace latinime
#endif // LATINIME_WORDS_PRIORITY_QUEUE_H
