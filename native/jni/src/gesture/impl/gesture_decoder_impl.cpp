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

#include "gesture_decoder_impl.h"
#include "incremental_decoder_interface.h"

namespace latinime {

// A factory method for GestureDecoderImpl
static IncrementalDecoderInterface *getDecoderInstance(int maxWordLength, int maxWords) {
    return new GestureDecoderImpl(maxWordLength, maxWords);
}

// An ad-hoc internal class to register the factory method defined above
class GestureDecoderFactoryRegisterer {
 public:
    GestureDecoderFactoryRegisterer() {
        IncrementalDecoderInterface::setGestureDecoderFactoryMethod(getDecoderInstance);
    }
 private:
    DISALLOW_COPY_AND_ASSIGN(GestureDecoderFactoryRegisterer);
};

// To invoke the GestureDecoderFactoryRegisterer constructor in the global constructor
// Not sure, but can be static?
GestureDecoderFactoryRegisterer gestureDecoderFactoryRegisterer;
} // namespace latinime
