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

#ifndef LATINIME_EXCLUSIVE_OWNERSHIP_POINTER_H
#define LATINIME_EXCLUSIVE_OWNERSHIP_POINTER_H

#include "defines.h"

namespace latinime {

template<class T>
class ExclusiveOwnershipPointer {
 public:
    // This instance become an owner of the raw pointer.
    AK_FORCE_INLINE ExclusiveOwnershipPointer(T *const rawPointer)
            : mPointer(rawPointer),
              mSharedOwnerPtr(new (ExclusiveOwnershipPointer<T> *)(this)) {}

    // Move the ownership.
    AK_FORCE_INLINE ExclusiveOwnershipPointer(const ExclusiveOwnershipPointer<T> &pointer)
            : mPointer(pointer.mPointer), mSharedOwnerPtr(pointer.mSharedOwnerPtr) {
        transferOwnership(&pointer);
    }

    AK_FORCE_INLINE ~ExclusiveOwnershipPointer() {
        deletePointersIfHavingOwnership();
    }

    // Move the ownership.
    AK_FORCE_INLINE ExclusiveOwnershipPointer<T> &operator=(
            const ExclusiveOwnershipPointer<T> &pointer) {
        // Delete pointers when this is an owner of another pointer.
        deletePointersIfHavingOwnership();
        mPointer = pointer.mPointer;
        mSharedOwnerPtr = pointer.mSharedOwnerPtr;
        transferOwnership(pointer);
        return *this;
    }

    AK_FORCE_INLINE T *get() const {
        return mPointer;
    }

 private:
    // This class allows to copy and assign and ensures only one instance has the ownership of the
    // managed pointer.
    DISALLOW_DEFAULT_CONSTRUCTOR(ExclusiveOwnershipPointer);

    void transferOwnership(const ExclusiveOwnershipPointer<T> *const src) {
        if (*mSharedOwnerPtr != src) {
           AKLOGE("Failed to transfer the ownership because src is not the current owner."
                   "src: %p, owner: %p", src, *mSharedOwnerPtr);
           ASSERT(false);
           return;
        }
        // Transfer the ownership from src to this instance.
        *mSharedOwnerPtr = this;
    }

    void deletePointersIfHavingOwnership() {
        if (mSharedOwnerPtr && *mSharedOwnerPtr == this) {
            if (mPointer) {
                if (DEBUG_DICT) {
                    AKLOGI("Releasing pointer: %p", mPointer);
                }
                delete mPointer;
            }
            delete mSharedOwnerPtr;
        }
    }

    T *mPointer;
    // mSharedOwnerPtr points a shared memory space where the instance which has the ownership is
    // stored.
    ExclusiveOwnershipPointer<T> **mSharedOwnerPtr;
};
} // namespace latinime
#endif /* LATINIME_EXCLUSIVE_OWNERSHIP_POINTER_H */
