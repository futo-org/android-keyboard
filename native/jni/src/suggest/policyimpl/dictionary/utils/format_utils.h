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

#ifndef LATINIME_FORMAT_UTILS_H
#define LATINIME_FORMAT_UTILS_H

#include <stdint.h>

#include "defines.h"

namespace latinime {

/**
 * Methods to handle binary dictionary format version.
 */
class FormatUtils {
 public:
    enum FORMAT_VERSION {
        VERSION_2,
        VERSION_3,
        UNKNOWN_VERSION
    };

    static FORMAT_VERSION detectFormatVersion(const uint8_t *const dict, const int dictSize);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(FormatUtils);

    static const int DICTIONARY_MINIMUM_SIZE;
    static const uint32_t HEADER_VERSION_2_MAGIC_NUMBER;
    static const int HEADER_VERSION_2_MINIMUM_SIZE;
};
} // namespace latinime
#endif /* LATINIME_FORMAT_UTILS_H */
