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
            LOGE("SuggestedWord is accidentally null.");
            return;
        }
        if (DEBUG_WORDS_PRIORITY_QUEUE) {
            LOGI("Push word. %d, %d", score, wordLength);
            DUMP_WORD(word, wordLength);
        }
        mSuggestions.push(sw);
    }

    SuggestedWord* topAndPop() {
        if (mSuggestions.empty()) return 0;
        SuggestedWord* sw = mSuggestions.top();
        mSuggestions.pop();
        return sw;
    }

    int outputSuggestions(int *frequencies, unsigned short *outputChars) {
        const unsigned int size = min(MAX_WORDS, mSuggestions.size());
        int index = size - 1;
        while (!mSuggestions.empty() && index >= 0) {
            SuggestedWord* sw = mSuggestions.top();
            if (DEBUG_WORDS_PRIORITY_QUEUE) {
                LOGI("dump word. %d", sw->mScore);
                DUMP_WORD(sw->mWord, sw->mWordLength);
            }
            const unsigned int wordLength = sw->mWordLength;
            char* targetAdr = (char*) outputChars
                    + (index) * MAX_WORD_LENGTH * sizeof(short);
            frequencies[index] = sw->mScore;
            memcpy(targetAdr, sw->mWord, (wordLength) * sizeof(short));
            if (wordLength < MAX_WORD_LENGTH) {
                ((unsigned short*) targetAdr)[wordLength] = 0;
            }
            sw->mUsed = false;
            mSuggestions.pop();
            --index;
        }
        return size;
    }

    int size() {
        return mSuggestions.size();
    }

    void clear() {
        while (!mSuggestions.empty()) {
            SuggestedWord* sw = mSuggestions.top();
            if (DEBUG_WORDS_PRIORITY_QUEUE) {
                LOGI("Clear word. %d", sw->mScore);
                DUMP_WORD(sw->mWord, sw->mWordLength);
            }
            sw->mUsed = false;
            mSuggestions.pop();
        }
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

    typedef std::priority_queue<SuggestedWord*, std::vector<SuggestedWord*>,
            wordComparator> Suggestions;
    Suggestions mSuggestions;
    const unsigned int MAX_WORDS;
    const unsigned int MAX_WORD_LENGTH;
    SuggestedWord* mSuggestedWords;
};
}

#endif // LATINIME_WORDS_PRIORITY_QUEUE_H
