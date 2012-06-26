/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef LATINIME_GESTURE_DECODER_H
#define LATINIME_GESTURE_DECODER_H

#include "defines.h"
#include "gesture_decoder_impl.h"

namespace latinime {

class GestureDecoder : public GestureDecoderImpl {

 public:
    GestureDecoder(int maxWordLength, int maxWords) :
            GestureDecoderImpl(maxWordLength, maxWords) {
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(GestureDecoder);
};
} // namespace latinime

#endif // LATINIME_INCREMENTAL_DECODER_H
