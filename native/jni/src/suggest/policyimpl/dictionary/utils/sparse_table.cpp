/*
 * Copyright (C) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "suggest/policyimpl/dictionary/utils/sparse_table.h"

namespace latinime {

const int SparseTable::NOT_EXIST = -1;

bool SparseTable::contains(const int id) const {
    const int readingPos = id / mBlockSize * mDataSize;
    if (id < 0 || mIndexTableBuffer->getTailPosition() <= readingPos) {
        return false;
    }
    const int index = mIndexTableBuffer->readUint(mDataSize, readingPos);
    return index != NOT_EXIST;
}

uint32_t SparseTable::get(const int id) const {
    const int indexTableIndex = id / mBlockSize;
    int readingPos = indexTableIndex * mDataSize;
    const int index = mIndexTableBuffer->readUint(mDataSize, readingPos);
    const int offset = id % mBlockSize;
    readingPos = (index * mDataSize + offset) * mBlockSize;
    return mContentTableBuffer->readUint(mDataSize, readingPos);
}

} // namespace latinime
