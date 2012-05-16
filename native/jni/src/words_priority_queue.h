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
#include <iostream>
#include <queue>
#include "defines.h"

namespace latinime {

class WordsPriorityQueue {
 public:
    class SuggestedWord {
    public:
        int mScore;
        unsigned short mWord[MAX_WORD_LENGTH_INTERNAL];
        int mWordLength;
        bool mUsed;

        void setParams(int score, unsigned short* word, int wordLength) {
            mScore = score;
            mWordLength = wordLength;
            memcpy(mWord, word, sizeof(unsigned short) * wordLength);
            mUsed = true;
        }
    };

    WordsPriorityQueue(int maxWords, int maxWordLength) :
            MAX_WORDS((unsigned int) maxWords), MAX_WORD_LENGTH(
                    (unsigned int) maxWordLength) {
        mSuggestedWords = new SuggestedWord[maxWordLength];
        for (int i = 0; i < maxWordLength; ++i) {
            mSuggestedWords[i].mUsed = false;
        }
        mHighestSuggestedWord = 0;
    }

    ~WordsPriorityQueue() {
        delete[] mSuggestedWords;
    }

    void push(int score, unsigned short* word, int wordLength) {
        SuggestedWord* sw = 0;
        if (mSuggestions.size() >= MAX_WORDS) {
            sw = mSuggestions.top();
            const int minScore = sw->mScore;
            if (minScore >= score) {
                return;
            } else {
                sw->mUsed = false;
                mSuggestions.pop();
            }
        }
        if (sw == 0) {
            sw = getFreeSuggestedWord(score, word, wordLength);
        } else {
            sw->setParams(score, word, wordLength);
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

    SuggestedWord* top() {
        if (mSuggestions.empty()) return 0;
        SuggestedWord* sw = mSuggestions.top();
        return sw;
    }

    int outputSuggestions(const unsigned short* before, const int beforeLength,
            int *frequencies, unsigned short *outputChars) {
        mHighestSuggestedWord = 0;
        const unsigned int size = min(
              MAX_WORDS, static_cast<unsigned int>(mSuggestions.size()));
        SuggestedWord* swBuffer[size];
        int index = size - 1;
        while (!mSuggestions.empty() && index >= 0) {
            SuggestedWord* sw = mSuggestions.top();
            if (DEBUG_WORDS_PRIORITY_QUEUE) {
                AKLOGI("dump word. %d", sw->mScore);
                DUMP_WORD(sw->mWord, sw->mWordLength);
            }
            swBuffer[index] = sw;
            mSuggestions.pop();
            --index;
        }
        if (size >= 2) {
            SuggestedWord* nsMaxSw = 0;
            unsigned int maxIndex = 0;
            float maxNs = 0;
            for (unsigned int i = 0; i < size; ++i) {
                SuggestedWord* tempSw = swBuffer[i];
                if (!tempSw) {
                    continue;
                }
                const float tempNs = getNormalizedScore(tempSw, before, beforeLength, 0, 0, 0);
                if (tempNs >= maxNs) {
                    maxNs = tempNs;
                    maxIndex = i;
                    nsMaxSw = tempSw;
                }
            }
            if (maxIndex > 0 && nsMaxSw) {
                memmove(&swBuffer[1], &swBuffer[0], maxIndex * sizeof(SuggestedWord*));
                swBuffer[0] = nsMaxSw;
            }
        }
        for (unsigned int i = 0; i < size; ++i) {
            SuggestedWord* sw = swBuffer[i];
            if (!sw) {
                AKLOGE("SuggestedWord is null %d", i);
                continue;
            }
            const unsigned int wordLength = sw->mWordLength;
            char* targetAdr = (char*) outputChars + i * MAX_WORD_LENGTH * sizeof(short);
            frequencies[i] = sw->mScore;
            memcpy(targetAdr, sw->mWord, (wordLength) * sizeof(short));
            if (wordLength < MAX_WORD_LENGTH) {
                ((unsigned short*) targetAdr)[wordLength] = 0;
            }
            sw->mUsed = false;
        }
        return size;
    }

    int size() const {
        return mSuggestions.size();
    }

    void clear() {
        mHighestSuggestedWord = 0;
        while (!mSuggestions.empty()) {
            SuggestedWord* sw = mSuggestions.top();
            if (DEBUG_WORDS_PRIORITY_QUEUE) {
                AKLOGI("Clear word. %d", sw->mScore);
                DUMP_WORD(sw->mWord, sw->mWordLength);
            }
            sw->mUsed = false;
            mSuggestions.pop();
        }
    }

    void dumpTopWord() {
        if (size() <= 0) {
            return;
        }
        DUMP_WORD(mHighestSuggestedWord->mWord, mHighestSuggestedWord->mWordLength);
    }

    float getHighestNormalizedScore(const unsigned short* before, const int beforeLength,
            unsigned short** outWord, int *outScore, int *outLength) {
        if (!mHighestSuggestedWord) {
            return 0.0;
        }
        return getNormalizedScore(
                mHighestSuggestedWord, before, beforeLength, outWord, outScore, outLength);
    }

 private:
    struct wordComparator {
        bool operator ()(SuggestedWord * left, SuggestedWord * right) {
            return left->mScore > right->mScore;
        }
    };

    SuggestedWord* getFreeSuggestedWord(int score, unsigned short* word,
            int wordLength) {
        for (unsigned int i = 0; i < MAX_WORD_LENGTH; ++i) {
            if (!mSuggestedWords[i].mUsed) {
                mSuggestedWords[i].setParams(score, word, wordLength);
                return &mSuggestedWords[i];
            }
        }
        return 0;
    }

    static float getNormalizedScore(SuggestedWord* sw, const unsigned short* before,
            const int beforeLength, unsigned short** outWord, int *outScore, int *outLength) {
        const int score = sw->mScore;
        unsigned short* word = sw->mWord;
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
        return Correction::RankingAlgorithm::calcNormalizedScore(
                before, beforeLength, word, wordLength, score);
    }

    typedef std::priority_queue<SuggestedWord*, std::vector<SuggestedWord*>,
            wordComparator> Suggestions;
    Suggestions mSuggestions;
    const unsigned int MAX_WORDS;
    const unsigned int MAX_WORD_LENGTH;
    SuggestedWord* mSuggestedWords;
    SuggestedWord* mHighestSuggestedWord;
};
}

#endif // LATINIME_WORDS_PRIORITY_QUEUE_H
