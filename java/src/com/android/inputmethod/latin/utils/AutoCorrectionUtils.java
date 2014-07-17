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

package com.android.inputmethod.latin.utils;

import android.util.Log;

import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.define.DebugFlags;

public final class AutoCorrectionUtils {
    private static final boolean DBG = DebugFlags.DEBUG_ENABLED;
    private static final String TAG = AutoCorrectionUtils.class.getSimpleName();
    private static final int MINIMUM_SAFETY_NET_CHAR_LENGTH = 4;

    private AutoCorrectionUtils() {
        // Purely static class: can't instantiate.
    }

    public static boolean suggestionExceedsAutoCorrectionThreshold(
            final SuggestedWordInfo suggestion, final String consideredWord,
            final float autoCorrectionThreshold) {
        if (null != suggestion) {
            // Shortlist a whitelisted word
            if (suggestion.isKindOf(SuggestedWordInfo.KIND_WHITELIST)) {
                return true;
            }
            final int autoCorrectionSuggestionScore = suggestion.mScore;
            // TODO: when the normalized score of the first suggestion is nearly equals to
            //       the normalized score of the second suggestion, behave less aggressive.
            final float normalizedScore = BinaryDictionaryUtils.calcNormalizedScore(
                    consideredWord, suggestion.mWord, autoCorrectionSuggestionScore);
            if (DBG) {
                Log.d(TAG, "Normalized " + consideredWord + "," + suggestion + ","
                        + autoCorrectionSuggestionScore + ", " + normalizedScore
                        + "(" + autoCorrectionThreshold + ")");
            }
            if (normalizedScore >= autoCorrectionThreshold) {
                if (DBG) {
                    Log.d(TAG, "Auto corrected by S-threshold.");
                }
                return !shouldBlockAutoCorrectionBySafetyNet(consideredWord, suggestion.mWord);
            }
        }
        return false;
    }

    // TODO: Resolve the inconsistencies between the native auto correction algorithms and
    // this safety net
    public static boolean shouldBlockAutoCorrectionBySafetyNet(final String typedWord,
            final String suggestion) {
        // Safety net for auto correction.
        // Actually if we hit this safety net, it's a bug.
        // If user selected aggressive auto correction mode, there is no need to use the safety
        // net.
        // If the length of typed word is less than MINIMUM_SAFETY_NET_CHAR_LENGTH,
        // we should not use net because relatively edit distance can be big.
        final int typedWordLength = typedWord.length();
        if (typedWordLength < MINIMUM_SAFETY_NET_CHAR_LENGTH) {
            return false;
        }
        final int maxEditDistanceOfNativeDictionary = (typedWordLength / 2) + 1;
        final int distance = BinaryDictionaryUtils.editDistance(typedWord, suggestion);
        if (DBG) {
            Log.d(TAG, "Autocorrected edit distance = " + distance
                    + ", " + maxEditDistanceOfNativeDictionary);
        }
        if (distance > maxEditDistanceOfNativeDictionary) {
            if (DBG) {
                Log.e(TAG, "Safety net: before = " + typedWord + ", after = " + suggestion);
                Log.e(TAG, "(Error) The edit distance of this correction exceeds limit. "
                        + "Turning off auto-correction.");
            }
            return true;
        } else {
            return false;
        }
    }
}
