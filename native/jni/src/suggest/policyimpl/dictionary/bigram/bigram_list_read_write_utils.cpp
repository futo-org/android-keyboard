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

#include "suggest/policyimpl/dictionary/bigram/bigram_list_read_write_utils.h"

#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_reading_utils.h"
#include "suggest/policyimpl/dictionary/utils/byte_array_utils.h"
#include "suggest/policyimpl/dictionary/utils/buffer_with_extendable_buffer.h"

namespace latinime {

const BigramListReadWriteUtils::BigramFlags BigramListReadWriteUtils::MASK_ATTRIBUTE_ADDRESS_TYPE =
        0x30;
const BigramListReadWriteUtils::BigramFlags
        BigramListReadWriteUtils::FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE = 0x10;
const BigramListReadWriteUtils::BigramFlags
        BigramListReadWriteUtils::FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES = 0x20;
const BigramListReadWriteUtils::BigramFlags
        BigramListReadWriteUtils::FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES = 0x30;
const BigramListReadWriteUtils::BigramFlags
        BigramListReadWriteUtils::FLAG_ATTRIBUTE_OFFSET_NEGATIVE = 0x40;
// Flag for presence of more attributes
const BigramListReadWriteUtils::BigramFlags BigramListReadWriteUtils::FLAG_ATTRIBUTE_HAS_NEXT =
        0x80;
// Mask for attribute probability, stored on 4 bits inside the flags byte.
const BigramListReadWriteUtils::BigramFlags
        BigramListReadWriteUtils::MASK_ATTRIBUTE_PROBABILITY = 0x0F;
const int BigramListReadWriteUtils::ATTRIBUTE_ADDRESS_SHIFT = 4;

/* static */ void BigramListReadWriteUtils::getBigramEntryPropertiesAndAdvancePosition(
        const uint8_t *const bigramsBuf, BigramFlags *const outBigramFlags,
        int *const outTargetPtNodePos, int *const bigramEntryPos) {
    const BigramFlags bigramFlags = ByteArrayUtils::readUint8AndAdvancePosition(bigramsBuf,
            bigramEntryPos);
    if (outBigramFlags) {
        *outBigramFlags = bigramFlags;
    }
    const int targetPos = getBigramAddressAndAdvancePosition(bigramsBuf, bigramFlags,
            bigramEntryPos);
    if (outTargetPtNodePos) {
        *outTargetPtNodePos = targetPos;
    }
}

/* static */ void BigramListReadWriteUtils::skipExistingBigrams(const uint8_t *const bigramsBuf,
        int *const bigramListPos) {
    BigramFlags flags;
    do {
        getBigramEntryPropertiesAndAdvancePosition(bigramsBuf, &flags, 0 /* outTargetPtNodePos */,
                bigramListPos);
    } while(hasNext(flags));
}

/* static */ int BigramListReadWriteUtils::getBigramAddressAndAdvancePosition(
        const uint8_t *const bigramsBuf, const BigramFlags flags, int *const pos) {
    int offset = 0;
    const int origin = *pos;
    switch (MASK_ATTRIBUTE_ADDRESS_TYPE & flags) {
        case FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE:
            offset = ByteArrayUtils::readUint8AndAdvancePosition(bigramsBuf, pos);
            break;
        case FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES:
            offset = ByteArrayUtils::readUint16AndAdvancePosition(bigramsBuf, pos);
            break;
        case FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES:
            offset = ByteArrayUtils::readUint24AndAdvancePosition(bigramsBuf, pos);
            break;
    }
    if (offset == DynamicPatriciaTrieReadingUtils::DICT_OFFSET_INVALID) {
        return NOT_A_DICT_POS;
    } else if (offset == DynamicPatriciaTrieReadingUtils::DICT_OFFSET_ZERO_OFFSET) {
        return origin;
    }
    if (isOffsetNegative(flags)) {
        return origin - offset;
    } else {
        return origin + offset;
    }
}

/* static */ bool BigramListReadWriteUtils::setHasNextFlag(
        BufferWithExtendableBuffer *const buffer, const bool hasNext, const int entryPos) {
    const bool usesAdditionalBuffer = buffer->isInAdditionalBuffer(entryPos);
    int readingPos = entryPos;
    if (usesAdditionalBuffer) {
        readingPos -= buffer->getOriginalBufferSize();
    }
    BigramFlags bigramFlags = ByteArrayUtils::readUint8AndAdvancePosition(
            buffer->getBuffer(usesAdditionalBuffer), &readingPos);
    if (hasNext) {
        bigramFlags = bigramFlags | FLAG_ATTRIBUTE_HAS_NEXT;
    } else {
        bigramFlags = bigramFlags & (~FLAG_ATTRIBUTE_HAS_NEXT);
    }
    int writingPos = entryPos;
    return buffer->writeUintAndAdvancePosition(bigramFlags, 1 /* size */, &writingPos);
}

/* static */ bool BigramListReadWriteUtils::createAndWriteBigramEntry(
        BufferWithExtendableBuffer *const buffer, const int targetPos, const int probability,
        const bool hasNext, int *const writingPos) {
    BigramFlags flags;
    if (!createAndGetBigramFlags(*writingPos, targetPos, probability, hasNext, &flags)) {
        return false;
    }
    return writeBigramEntry(buffer, flags, targetPos, writingPos);
}

/* static */ bool BigramListReadWriteUtils::writeBigramEntry(
        BufferWithExtendableBuffer *const bufferToWrite, const BigramFlags flags,
        const int targetPtNodePos, int *const writingPos) {
    const int offset = getBigramTargetOffset(targetPtNodePos, *writingPos);
    const BigramFlags flagsToWrite = (offset < 0) ?
            (flags | FLAG_ATTRIBUTE_OFFSET_NEGATIVE) : (flags & ~FLAG_ATTRIBUTE_OFFSET_NEGATIVE);
    if (!bufferToWrite->writeUintAndAdvancePosition(flagsToWrite, 1 /* size */, writingPos)) {
        return false;
    }
    const uint32_t absOffest = abs(offset);
    const int bigramTargetFieldSize = attributeAddressSize(flags);
    return bufferToWrite->writeUintAndAdvancePosition(absOffest, bigramTargetFieldSize,
            writingPos);
}

// Returns true if the bigram entry is valid and put entry flags into out*.
/* static */ bool BigramListReadWriteUtils::createAndGetBigramFlags(const int entryPos,
        const int targetPtNodePos, const int probability, const bool hasNext,
        BigramFlags *const outBigramFlags) {
    BigramFlags flags = probability & MASK_ATTRIBUTE_PROBABILITY;
    if (hasNext) {
        flags |= FLAG_ATTRIBUTE_HAS_NEXT;
    }
    const int offset = getBigramTargetOffset(targetPtNodePos, entryPos);
    if (offset < 0) {
        flags |= FLAG_ATTRIBUTE_OFFSET_NEGATIVE;
    }
    const uint32_t absOffest = abs(offset);
    if ((absOffest >> 24) != 0) {
        // Offset is too large.
        return false;
    } else if ((absOffest >> 16) != 0) {
        flags |= FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES;
    } else if ((absOffest >> 8) != 0) {
        flags |= FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES;
    } else {
        flags |= FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE;
    }
    // Currently, all newly written bigram position fields are 3 bytes to simplify dictionary
    // writing.
    // TODO: Remove following 2 lines and optimize memory space.
    flags = (flags & (~MASK_ATTRIBUTE_ADDRESS_TYPE)) | FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES;
    *outBigramFlags = flags;
    return true;
}

/* static */ int BigramListReadWriteUtils::getBigramTargetOffset(const int targetPtNodePos,
        const int entryPos) {
    if (targetPtNodePos == NOT_A_DICT_POS) {
        return DynamicPatriciaTrieReadingUtils::DICT_OFFSET_INVALID;
    } else {
        const int offset = targetPtNodePos - (entryPos + 1 /* bigramFlagsField */);
        if (offset == 0) {
            return DynamicPatriciaTrieReadingUtils::DICT_OFFSET_ZERO_OFFSET;
        } else {
            return offset;
        }
    }
}

} // namespace latinime
