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

#ifndef LATINIME_INT_ARRAY_VIEW_H
#define LATINIME_INT_ARRAY_VIEW_H

#include <cstdint>
#include <cstdlib>
#include <vector>

#include "defines.h"

namespace latinime {

/**
 * Helper class used to provide a read-only view of a given range of integer array. This class
 * does not take ownership of the underlying integer array but is designed to be a lightweight
 * object that obeys value semantics.
 *
 * Example:
 * <code>
 * bool constinsX(IntArrayView view) {
 *     for (size_t i = 0; i < view.size(); ++i) {
 *         if (view[i] == 'X') {
 *             return true;
 *         }
 *     }
 *     return false;
 * }
 *
 * const int codePointArray[] = { 'A', 'B', 'X', 'Z' };
 * auto view = IntArrayView(codePointArray, NELEMS(codePointArray));
 * const bool hasX = constinsX(view);
 * </code>
 */
class IntArrayView {
 public:
    IntArrayView() : mPtr(nullptr), mSize(0) {}

    IntArrayView(const int *const ptr, const size_t size)
            : mPtr(ptr), mSize(size) {}

    explicit IntArrayView(const std::vector<int> &vector)
            : mPtr(vector.data()), mSize(vector.size()) {}

    template <int N>
    AK_FORCE_INLINE static IntArrayView fromFixedSizeArray(const int (&array)[N]) {
        return IntArrayView(array, N);
    }

    // Returns a view that points one int object. Does not take ownership of the given object.
    AK_FORCE_INLINE static IntArrayView fromObject(const int *const object) {
        return IntArrayView(object, 1);
    }

    AK_FORCE_INLINE int operator[](const size_t index) const {
        ASSERT(index < mSize);
        return mPtr[index];
    }

    AK_FORCE_INLINE bool empty() const {
        return size() == 0;
    }

    AK_FORCE_INLINE size_t size() const {
        return mSize;
    }

    AK_FORCE_INLINE const int *data() const {
        return mPtr;
    }

    AK_FORCE_INLINE const int *begin() const {
        return mPtr;
    }

    AK_FORCE_INLINE const int *end() const {
        return mPtr + mSize;
    }

 private:
    DISALLOW_ASSIGNMENT_OPERATOR(IntArrayView);

    const int *const mPtr;
    const size_t mSize;
};

using WordIdArrayView = IntArrayView;
using PtNodePosArrayView = IntArrayView;

} // namespace latinime
#endif // LATINIME_MEMORY_VIEW_H
