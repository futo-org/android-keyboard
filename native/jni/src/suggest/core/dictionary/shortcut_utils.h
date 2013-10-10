/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef LATINIME_SHORTCUT_UTILS
#define LATINIME_SHORTCUT_UTILS

#include "defines.h"
#include "suggest/core/dicnode/dic_node_utils.h"
#include "suggest/core/dictionary/binary_dictionary_shortcut_iterator.h"

namespace latinime {

class ShortcutUtils {
 public:
    static int outputShortcuts(BinaryDictionaryShortcutIterator *const shortcutIt,
            int outputWordIndex, const int finalScore, int *const outputCodePoints,
            int *const frequencies, int *const outputTypes, const bool sameAsTyped) {
        int shortcutTarget[MAX_WORD_LENGTH];
        while (shortcutIt->hasNextShortcutTarget() && outputWordIndex < MAX_RESULTS) {
            bool isWhilelist;
            int shortcutTargetStringLength;
            shortcutIt->nextShortcutTarget(MAX_WORD_LENGTH, shortcutTarget,
                    &shortcutTargetStringLength, &isWhilelist);
            int shortcutScore;
            int kind;
            if (isWhilelist && sameAsTyped) {
                shortcutScore = S_INT_MAX;
                kind = Dictionary::KIND_WHITELIST;
            } else {
                // shortcut entry's score == its base entry's score - 1
                shortcutScore = finalScore;
                // Protection against int underflow
                shortcutScore = max(S_INT_MIN + 1, shortcutScore) - 1;
                kind = Dictionary::KIND_SHORTCUT;
            }
            outputTypes[outputWordIndex] = kind;
            frequencies[outputWordIndex] = shortcutScore;
            frequencies[outputWordIndex] = max(S_INT_MIN + 1, shortcutScore) - 1;
            const int startIndex2 = outputWordIndex * MAX_WORD_LENGTH;
            DicNodeUtils::appendTwoWords(0, 0, shortcutTarget, shortcutTargetStringLength,
                    &outputCodePoints[startIndex2]);
            ++outputWordIndex;
        }
        return outputWordIndex;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(ShortcutUtils);
};
} // namespace latinime
#endif // LATINIME_SHORTCUT_UTILS
