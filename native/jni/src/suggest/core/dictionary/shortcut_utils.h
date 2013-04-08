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
#include "terminal_attributes.h"

namespace latinime {

class ShortcutUtils {
 public:
    static int outputShortcuts(const TerminalAttributes *const terminalAttributes,
            int outputWordIndex, const int finalScore, int *const outputCodePoints,
            int *const frequencies, int *const outputTypes, const bool sameAsTyped) {
        TerminalAttributes::ShortcutIterator iterator = terminalAttributes->getShortcutIterator();
        while (iterator.hasNextShortcutTarget() && outputWordIndex < MAX_RESULTS) {
            int shortcutTarget[MAX_WORD_LENGTH];
            int shortcutProbability;
            const int shortcutTargetStringLength = iterator.getNextShortcutTarget(
                    MAX_WORD_LENGTH, shortcutTarget, &shortcutProbability);
            int shortcutScore;
            int kind;
            if (shortcutProbability == BinaryFormat::WHITELIST_SHORTCUT_PROBABILITY
                    && sameAsTyped) {
                shortcutScore = S_INT_MAX;
                kind = Dictionary::KIND_WHITELIST;
            } else {
                // shortcut entry's score == its base entry's score - 1
                shortcutScore = finalScore;
                // Protection against int underflow
                shortcutScore = max(S_INT_MIN + 1, shortcutScore) - 1;
                kind = Dictionary::KIND_CORRECTION;
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
