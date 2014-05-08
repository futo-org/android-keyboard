/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef LATINIME_WORD_PROPERTY_H
#define LATINIME_WORD_PROPERTY_H

#include <vector>

#include "defines.h"
#include "jni.h"
#include "suggest/core/dictionary/property/bigram_property.h"
#include "suggest/core/dictionary/property/unigram_property.h"

namespace latinime {

// This class is used for returning information belonging to a word to java side.
class WordProperty {
 public:
    // Default constructor is used to create an instance that indicates an invalid word.
    WordProperty()
            : mCodePoints(), mUnigramProperty(), mBigrams() {}

    WordProperty(const std::vector<int> *const codePoints,
            const UnigramProperty *const unigramProperty,
            const std::vector<BigramProperty> *const bigrams)
            : mCodePoints(*codePoints), mUnigramProperty(*unigramProperty), mBigrams(*bigrams) {}

    void outputProperties(JNIEnv *const env, jintArray outCodePoints, jbooleanArray outFlags,
            jintArray outProbabilityInfo, jobject outBigramTargets, jobject outBigramProbabilities,
            jobject outShortcutTargets, jobject outShortcutProbabilities) const;

    const UnigramProperty *getUnigramProperty() const {
        return &mUnigramProperty;
    }

    const std::vector<BigramProperty> *getBigramProperties() const {
        return &mBigrams;
    }

 private:
    // Default copy constructor is used for using as a return value.
    DISALLOW_ASSIGNMENT_OPERATOR(WordProperty);

    const std::vector<int> mCodePoints;
    const UnigramProperty mUnigramProperty;
    const std::vector<BigramProperty> mBigrams;
};
} // namespace latinime
#endif // LATINIME_WORD_PROPERTY_H
