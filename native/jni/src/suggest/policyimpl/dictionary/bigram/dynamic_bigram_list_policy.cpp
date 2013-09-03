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

#include "suggest/policyimpl/dictionary/bigram/dynamic_bigram_list_policy.h"

namespace latinime {

bool DynamicBigramListPolicy::copyAllBigrams(int *const fromPos, int *const toPos) {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*fromPos);
    const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        *fromPos -= mBuffer->getOriginalBufferSize();
    }
    BigramListReadWriteUtils::BigramFlags flags;
    do {
        flags = BigramListReadWriteUtils::getFlagsAndForwardPointer(buffer, fromPos);
        int bigramPos = BigramListReadWriteUtils::getBigramAddressAndForwardPointer(
                buffer, flags, fromPos);
        if (bigramPos == NOT_A_VALID_WORD_POS) {
            // skip invalid bigram entry.
            continue;
        }
        if (usesAdditionalBuffer) {
            bigramPos += mBuffer->getOriginalBufferSize();
        }
        BigramListReadWriteUtils::BigramFlags newBigramFlags;
        uint32_t newBigramOffset;
        int newBigramOffsetFieldSize;
        if(!BigramListReadWriteUtils::createBigramEntryAndGetFlagsAndOffsetAndOffsetFieldSize(
                *toPos, bigramPos, BigramListReadWriteUtils::getProbabilityFromFlags(flags),
                BigramListReadWriteUtils::hasNext(flags), &newBigramFlags, &newBigramOffset,
                &newBigramOffsetFieldSize)) {
            continue;
        }
        // Write bigram entry. Target buffer is always the additional buffer.
        if (!mBuffer->writeUintAndAdvancePosition(newBigramFlags, 1 /* size */,toPos)) {
            return false;
        }
        if (!mBuffer->writeUintAndAdvancePosition(newBigramOffset, newBigramOffsetFieldSize,
                toPos)) {
            return false;
        }
    } while(BigramListReadWriteUtils::hasNext(flags));
    if (usesAdditionalBuffer) {
        *fromPos += mBuffer->getOriginalBufferSize();
    }
    return true;
}

bool DynamicBigramListPolicy::addBigramEntry(const int bigramPos, const int probability,
        int *const pos) {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*pos);
    const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        *pos -= mBuffer->getOriginalBufferSize();
    }
    BigramListReadWriteUtils::BigramFlags flags;
    do {
        int entryPos = *pos;
        if (usesAdditionalBuffer) {
            entryPos += mBuffer->getOriginalBufferSize();
        }
        flags = BigramListReadWriteUtils::getFlagsAndForwardPointer(buffer, pos);
        BigramListReadWriteUtils::getBigramAddressAndForwardPointer(buffer, flags, pos);
        if (BigramListReadWriteUtils::hasNext(flags)) {
            continue;
        }
        // The current last entry is found.
        // First, update the flags of the last entry.
        const BigramListReadWriteUtils::BigramFlags updatedFlags =
                BigramListReadWriteUtils::setHasNextFlag(flags);
        if (!mBuffer->writeUintAndAdvancePosition(updatedFlags, 1 /* size */, &entryPos)) {
            return false;
        }
        // Then, add a new entry after the last entry.
        BigramListReadWriteUtils::BigramFlags newBigramFlags;
        uint32_t newBigramOffset;
        int newBigramOffsetFieldSize;
        if(!BigramListReadWriteUtils::createBigramEntryAndGetFlagsAndOffsetAndOffsetFieldSize(
                *pos, bigramPos, BigramListReadWriteUtils::getProbabilityFromFlags(flags),
                BigramListReadWriteUtils::hasNext(flags), &newBigramFlags, &newBigramOffset,
                &newBigramOffsetFieldSize)) {
            continue;
        }
        int newEntryPos = *pos;
        if (usesAdditionalBuffer) {
            newEntryPos += mBuffer->getOriginalBufferSize();
        }
        // Write bigram flags.
        if (!mBuffer->writeUintAndAdvancePosition(newBigramFlags, 1 /* size */,
                &newEntryPos)) {
            return false;
        }
        // Write bigram positon offset.
        if (!mBuffer->writeUintAndAdvancePosition(newBigramOffset, newBigramOffsetFieldSize,
                &newEntryPos)) {
            return false;
        }
    } while(BigramListReadWriteUtils::hasNext(flags));
    if (usesAdditionalBuffer) {
        *pos += mBuffer->getOriginalBufferSize();
    }
    return true;
}

bool DynamicBigramListPolicy::removeBigram(const int bigramListPos, const int targetBigramPos) {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(bigramListPos);
    const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
    int pos = bigramListPos;
    if (usesAdditionalBuffer) {
        pos -= mBuffer->getOriginalBufferSize();
    }
    BigramListReadWriteUtils::BigramFlags flags;
    do {
        flags = BigramListReadWriteUtils::getFlagsAndForwardPointer(buffer, &pos);
        int bigramOffsetFieldPos = pos;
        if (usesAdditionalBuffer) {
            bigramOffsetFieldPos += mBuffer->getOriginalBufferSize();
        }
        int bigramPos = BigramListReadWriteUtils::getBigramAddressAndForwardPointer(
                buffer, flags, &pos);
        if (usesAdditionalBuffer && bigramPos != NOT_A_VALID_WORD_POS) {
            bigramPos += mBuffer->getOriginalBufferSize();
        }
        if (bigramPos != targetBigramPos) {
            continue;
        }
        // Target entry is found. Write 0 into the bigram pos field to mark the bigram invalid.
        const int bigramOffsetFieldSize =
                BigramListReadWriteUtils::attributeAddressSize(flags);
        if (!mBuffer->writeUintAndAdvancePosition(0 /* data */, bigramOffsetFieldSize,
                &bigramOffsetFieldPos)) {
            return false;
        }
        return true;
    } while(BigramListReadWriteUtils::hasNext(flags));
    return false;
}

} // namespace latinime
