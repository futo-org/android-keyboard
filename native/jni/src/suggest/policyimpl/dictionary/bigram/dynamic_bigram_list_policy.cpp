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

const int DynamicBigramListPolicy::BIGRAM_LINK_COUNT_LIMIT = 10000;

void DynamicBigramListPolicy::getNextBigram(int *const outBigramPos, int *const outProbability,
        bool *const outHasNext, int *const pos) const {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*pos);
    const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        *pos -= mBuffer->getOriginalBufferSize();
    }
    const BigramListReadWriteUtils::BigramFlags flags =
            BigramListReadWriteUtils::getFlagsAndForwardPointer(buffer, pos);
    int originalBigramPos = BigramListReadWriteUtils::getBigramAddressAndForwardPointer(
            buffer, flags, pos);
    if (usesAdditionalBuffer && originalBigramPos != NOT_A_DICT_POS) {
        originalBigramPos += mBuffer->getOriginalBufferSize();
    }
    *outBigramPos = followBigramLinkAndGetCurrentBigramPtNodePos(originalBigramPos);
    *outProbability = BigramListReadWriteUtils::getProbabilityFromFlags(flags);
    *outHasNext = BigramListReadWriteUtils::hasNext(flags);
    if (usesAdditionalBuffer) {
        *pos += mBuffer->getOriginalBufferSize();
    }
}

void DynamicBigramListPolicy::skipAllBigrams(int *const pos) const {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*pos);
    const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
    if (usesAdditionalBuffer) {
        *pos -= mBuffer->getOriginalBufferSize();
    }
    BigramListReadWriteUtils::skipExistingBigrams(buffer, pos);
    if (usesAdditionalBuffer) {
        *pos += mBuffer->getOriginalBufferSize();
    }
}

bool DynamicBigramListPolicy::copyAllBigrams(BufferWithExtendableBuffer *const bufferToWrite,
        int *const fromPos, int *const toPos, int *const outBigramsCount) const {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*fromPos);
    if (usesAdditionalBuffer) {
        *fromPos -= mBuffer->getOriginalBufferSize();
    }
    *outBigramsCount = 0;
    BigramListReadWriteUtils::BigramFlags flags;
    do {
        // The buffer address can be changed after calling buffer writing methods.
        const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
        flags = BigramListReadWriteUtils::getFlagsAndForwardPointer(buffer, fromPos);
        int originalBigramPos = BigramListReadWriteUtils::getBigramAddressAndForwardPointer(
                buffer, flags, fromPos);
        if (originalBigramPos == NOT_A_DICT_POS) {
            // skip invalid bigram entry.
            continue;
        }
        if (usesAdditionalBuffer) {
            originalBigramPos += mBuffer->getOriginalBufferSize();
        }
        const int bigramPos = followBigramLinkAndGetCurrentBigramPtNodePos(originalBigramPos);
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
        if (!bufferToWrite->writeUintAndAdvancePosition(newBigramFlags, 1 /* size */,toPos)) {
            return false;
        }
        if (!bufferToWrite->writeUintAndAdvancePosition(newBigramOffset, newBigramOffsetFieldSize,
                toPos)) {
            return false;
        }
        (*outBigramsCount)++;
    } while(BigramListReadWriteUtils::hasNext(flags));
    if (usesAdditionalBuffer) {
        *fromPos += mBuffer->getOriginalBufferSize();
    }
    return true;
}

bool DynamicBigramListPolicy::addNewBigramEntryToBigramList(const int bigramPos,
        const int probability, int *const pos) {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(*pos);
    if (usesAdditionalBuffer) {
        *pos -= mBuffer->getOriginalBufferSize();
    }
    BigramListReadWriteUtils::BigramFlags flags;
    do {
        int entryPos = *pos;
        if (usesAdditionalBuffer) {
            entryPos += mBuffer->getOriginalBufferSize();
        }
        // The buffer address can be changed after calling buffer writing methods.
        const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
        flags = BigramListReadWriteUtils::getFlagsAndForwardPointer(buffer, pos);
        int originalBigramPos = BigramListReadWriteUtils::getBigramAddressAndForwardPointer(
                buffer, flags, pos);
        if (usesAdditionalBuffer && originalBigramPos != NOT_A_DICT_POS) {
            originalBigramPos += mBuffer->getOriginalBufferSize();
        }
        if (followBigramLinkAndGetCurrentBigramPtNodePos(originalBigramPos) == bigramPos) {
            // Update this bigram entry.
            const BigramListReadWriteUtils::BigramFlags updatedFlags =
                    BigramListReadWriteUtils::setProbabilityInFlags(flags, probability);
            return mBuffer->writeUintAndAdvancePosition(updatedFlags, 1 /* size */, &entryPos);
        }
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
        if (usesAdditionalBuffer) {
            *pos += mBuffer->getOriginalBufferSize();
        }
        // Then, add a new entry after the last entry.
        return writeNewBigramEntry(bigramPos, probability, pos);
    } while(BigramListReadWriteUtils::hasNext(flags));
    // We return directly from the while loop.
    ASSERT(false);
    return false;
}

bool DynamicBigramListPolicy::writeNewBigramEntry(const int bigramPos, const int probability,
        int *const writingPos) {
    BigramListReadWriteUtils::BigramFlags newBigramFlags;
    uint32_t newBigramOffset;
    int newBigramOffsetFieldSize;
    if(!BigramListReadWriteUtils::createBigramEntryAndGetFlagsAndOffsetAndOffsetFieldSize(
            *writingPos, bigramPos, probability, false /* hasNext */, &newBigramFlags,
            &newBigramOffset, &newBigramOffsetFieldSize)) {
        return false;
    }
    // Write bigram flags.
    if (!mBuffer->writeUintAndAdvancePosition(newBigramFlags, 1 /* size */, writingPos)) {
        return false;
    }
    // Write bigram positon offset.
    if (!mBuffer->writeUintAndAdvancePosition(newBigramOffset, newBigramOffsetFieldSize,
            writingPos)) {
        return false;
    }
    return true;
}

bool DynamicBigramListPolicy::removeBigram(const int bigramListPos, const int targetBigramPos) {
    const bool usesAdditionalBuffer = mBuffer->isInAdditionalBuffer(bigramListPos);
    int pos = bigramListPos;
    if (usesAdditionalBuffer) {
        pos -= mBuffer->getOriginalBufferSize();
    }
    BigramListReadWriteUtils::BigramFlags flags;
    do {
        // The buffer address can be changed after calling buffer writing methods.
        const uint8_t *const buffer = mBuffer->getBuffer(usesAdditionalBuffer);
        flags = BigramListReadWriteUtils::getFlagsAndForwardPointer(buffer, &pos);
        int bigramOffsetFieldPos = pos;
        if (usesAdditionalBuffer) {
            bigramOffsetFieldPos += mBuffer->getOriginalBufferSize();
        }
        int originalBigramPos = BigramListReadWriteUtils::getBigramAddressAndForwardPointer(
                buffer, flags, &pos);
        if (usesAdditionalBuffer && originalBigramPos != NOT_A_DICT_POS) {
            originalBigramPos += mBuffer->getOriginalBufferSize();
        }
        const int bigramPos = followBigramLinkAndGetCurrentBigramPtNodePos(originalBigramPos);
        if (bigramPos != targetBigramPos) {
            continue;
        }
        // Target entry is found. Write 0 into the bigram pos field to mark the bigram invalid.
        const int bigramOffsetFieldSize = BigramListReadWriteUtils::attributeAddressSize(flags);
        if (!mBuffer->writeUintAndAdvancePosition(0 /* data */, bigramOffsetFieldSize,
                &bigramOffsetFieldPos)) {
            return false;
        }
        return true;
    } while(BigramListReadWriteUtils::hasNext(flags));
    return false;
}

int DynamicBigramListPolicy::followBigramLinkAndGetCurrentBigramPtNodePos(
        const int originalBigramPos) const {
    if (originalBigramPos == NOT_A_DICT_POS) {
        return NOT_A_DICT_POS;
    }
    int currentPos = originalBigramPos;
    DynamicPatriciaTrieNodeReader nodeReader(mBuffer, this /* bigramsPolicy */, mShortcutPolicy);
    nodeReader.fetchNodeInfoFromBuffer(currentPos);
    int bigramLinkCount = 0;
    while (nodeReader.getBigramLinkedNodePos() != NOT_A_DICT_POS) {
        currentPos = nodeReader.getBigramLinkedNodePos();
        nodeReader.fetchNodeInfoFromBuffer(currentPos);
        bigramLinkCount++;
        if (bigramLinkCount > BIGRAM_LINK_COUNT_LIMIT) {
            AKLOGI("Bigram link is invalid. start position: %d", bigramPos);
            ASSERT(false);
            return NOT_A_DICT_POS;
        }
    }
    return currentPos;
}

} // namespace latinime
