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

namespace latinime {

class BufferWithExtendableBuffer;

class BigramListReadWriteUtils {
public:
   typedef uint8_t BigramFlags;

   static void getBigramEntryPropertiesAndAdvancePosition(const uint8_t *const bigramsBuf,
           BigramFlags *const outBigramFlags, int *const outTargetPtNodePos,
           int *const bigramEntryPos);

   static AK_FORCE_INLINE int getProbabilityFromFlags(const BigramFlags flags) {
       return flags & MASK_ATTRIBUTE_PROBABILITY;
   }

   static AK_FORCE_INLINE bool hasNext(const BigramFlags flags) {
       return (flags & FLAG_ATTRIBUTE_HAS_NEXT) != 0;
   }

   // Bigrams reading methods
   static void skipExistingBigrams(const uint8_t *const bigramsBuf, int *const bigramListPos);

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

   static bool setHasNextFlag(BufferWithExtendableBuffer *const buffer,
           const bool hasNext, const int entryPos);

   static AK_FORCE_INLINE BigramFlags setProbabilityInFlags(const BigramFlags flags,
           const int probability) {
       return (flags & (~MASK_ATTRIBUTE_PROBABILITY)) | (probability & MASK_ATTRIBUTE_PROBABILITY);
   }

   static bool createAndWriteBigramEntry(BufferWithExtendableBuffer *const buffer,
           const int targetPos, const int probability, const bool hasNext, int *const writingPos);

   static bool writeBigramEntry(BufferWithExtendableBuffer *const buffer, const BigramFlags flags,
           const int targetOffset, int *const writingPos);

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

   // Returns true if the bigram entry is valid and put entry flags into out*.
   static bool createAndGetBigramFlags(const int entryPos, const int targetPos,
           const int probability, const bool hasNext, BigramFlags *const outBigramFlags);

   static AK_FORCE_INLINE bool isOffsetNegative(const BigramFlags flags) {
       return (flags & FLAG_ATTRIBUTE_OFFSET_NEGATIVE) != 0;
   }

   static int getBigramAddressAndAdvancePosition(const uint8_t *const bigramsBuf,
           const BigramFlags flags, int *const pos);

   static int getBigramTargetOffset(const int targetPtNodePos, const int entryPos);
};
} // namespace latinime
#endif // LATINIME_BIGRAM_LIST_READ_WRITE_UTILS_H
