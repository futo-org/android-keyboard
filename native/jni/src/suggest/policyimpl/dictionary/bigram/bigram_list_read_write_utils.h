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

#ifndef LATINIME_BIGRAM_LIST_READ_WRITE_UTILS_H
#define LATINIME_BIGRAM_LIST_READ_WRITE_UTILS_H

#include <cstdlib>
#include <stdint.h>

#include "defines.h"
#include "suggest/policyimpl/dictionary/utils/byte_array_utils.h"

namespace latinime {

class BigramListReadWriteUtils {
public:
   typedef uint8_t BigramFlags;

   static AK_FORCE_INLINE BigramFlags getFlagsAndForwardPointer(
           const uint8_t *const bigramsBuf, int *const pos) {
       return ByteArrayUtils::readUint8AndAdvancePosition(bigramsBuf, pos);
   }

   static AK_FORCE_INLINE int getProbabilityFromFlags(const BigramFlags flags) {
       return flags & MASK_ATTRIBUTE_PROBABILITY;
   }

   static AK_FORCE_INLINE bool hasNext(const BigramFlags flags) {
       return (flags & FLAG_ATTRIBUTE_HAS_NEXT) != 0;
   }

   // Bigrams reading methods
   static AK_FORCE_INLINE void skipExistingBigrams(const uint8_t *const bigramsBuf,
           int *const pos) {
       BigramFlags flags = getFlagsAndForwardPointer(bigramsBuf, pos);
       while (hasNext(flags)) {
           *pos += attributeAddressSize(flags);
           flags = getFlagsAndForwardPointer(bigramsBuf, pos);
       }
       *pos += attributeAddressSize(flags);
   }

   static int getBigramAddressAndForwardPointer(const uint8_t *const bigramsBuf,
           const BigramFlags flags, int *const pos);

   // Returns the size of the bigram position field that is stored in bigram flags.
   static AK_FORCE_INLINE int attributeAddressSize(const BigramFlags flags) {
       return (flags & MASK_ATTRIBUTE_ADDRESS_TYPE) >> ATTRIBUTE_ADDRESS_SHIFT;
       /* Note: this is a value-dependant optimization of what may probably be
          more readably written this way:
          switch (flags * BinaryFormat::MASK_ATTRIBUTE_ADDRESS_TYPE) {
          case FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE: return 1;
          case FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES: return 2;
          case FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTE: return 3;
          default: return 0;
          }
       */
   }

   static AK_FORCE_INLINE BigramFlags setHasNextFlag(const BigramFlags flags) {
       return flags | FLAG_ATTRIBUTE_HAS_NEXT;
   }

   // Returns true if the bigram entry is valid and put entry values into out*.
   static AK_FORCE_INLINE bool createBigramEntryAndGetFlagsAndOffsetAndOffsetFieldSize(
           const int entryPos, const int targetPos, const int probability, const bool hasNext,
           BigramFlags *const outBigramFlags, uint32_t *const outOffset,
           int *const outOffsetFieldSize) {
       if (targetPos == NOT_A_VALID_WORD_POS) {
           return false;
       }
       BigramFlags flags = probability & MASK_ATTRIBUTE_PROBABILITY;
       if (hasNext) {
           flags |= FLAG_ATTRIBUTE_HAS_NEXT;
       }
       const int targetFieldPos = entryPos + 1;
       const int offset = targetPos - targetFieldPos;
       if (offset < 0) {
           flags |= FLAG_ATTRIBUTE_OFFSET_NEGATIVE;
       }
       const uint32_t absOffest = abs(offset);
       if ((absOffest >> 24) != 0) {
           // Offset is too large.
           return false;
       } else if ((absOffest >> 16) != 0) {
           flags |= FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES;
           *outOffsetFieldSize = 3;
       } else if ((absOffest >> 8) != 0) {
           flags |= FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES;
           *outOffsetFieldSize = 2;
       } else {
           flags |= FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE;
           *outOffsetFieldSize = 1;
       }
       *outBigramFlags = flags;
       *outOffset = absOffest;
       return true;
   }

private:
   DISALLOW_IMPLICIT_CONSTRUCTORS(BigramListReadWriteUtils);

   static const BigramFlags MASK_ATTRIBUTE_ADDRESS_TYPE;
   static const BigramFlags FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE;
   static const BigramFlags FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES;
   static const BigramFlags FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES;
   static const BigramFlags FLAG_ATTRIBUTE_OFFSET_NEGATIVE;
   static const BigramFlags FLAG_ATTRIBUTE_HAS_NEXT;
   static const BigramFlags MASK_ATTRIBUTE_PROBABILITY;
   static const int ATTRIBUTE_ADDRESS_SHIFT;

   static AK_FORCE_INLINE bool isOffsetNegative(const BigramFlags flags) {
       return (flags & FLAG_ATTRIBUTE_OFFSET_NEGATIVE) != 0;
   }
};
} // namespace latinime
#endif // LATINIME_BIGRAM_LIST_READ_WRITE_UTILS_H
