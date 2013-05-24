/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef LATINIME_SUGGEST_OPTIONS_H
#define LATINIME_SUGGEST_OPTIONS_H

#include "defines.h"

namespace latinime {

class SuggestOptions{
 public:
    AK_FORCE_INLINE bool isGesture() const {
        return getBoolOption(IS_GESTURE);
    }

    AK_FORCE_INLINE bool useFullEditDistance() const {
        return getBoolOption(USE_FULL_EDIT_DISTANCE);
    }

    SuggestOptions(const int *const options, const int length)
            : mOptions(options), mLength(length) {}

 private:
    // Need to update com.android.inputmethod.latin.NativeSuggestOptions when you add, remove or
    // reorder options.
    static const int IS_GESTURE = 0;
    static const int USE_FULL_EDIT_DISTANCE = 1;

    const int *const mOptions;
    const int mLength;

    AK_FORCE_INLINE bool isValidKey(const int key) const {
        return 0 <= key && key < mLength;
    }

    AK_FORCE_INLINE bool getBoolOption(const int key) const {
        if (isValidKey(key)) {
            return mOptions[key] != 0;
        }
        return false;
    }

    AK_FORCE_INLINE int getIntOption(const int key) const {
        if (isValidKey(key)) {
            return mOptions[key];
        }
        return 0;
    }
};
} // namespace latinime
#endif // LATINIME_SUGGEST_OPTIONS_H
