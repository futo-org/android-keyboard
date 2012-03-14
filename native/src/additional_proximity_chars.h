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

#ifndef LATINIME_ADDITIONAL_PROXIMITY_CHARS_H
#define LATINIME_ADDITIONAL_PROXIMITY_CHARS_H

#include <stdint.h>
#include <string>

#include "defines.h"

namespace latinime {

class AdditionalProximityChars {
 private:
    static const std::string LOCALE_EN_US;
    static const int EN_US_ADDITIONAL_A_SIZE = 4;
    static const int32_t EN_US_ADDITIONAL_A[];
    static const int EN_US_ADDITIONAL_E_SIZE = 4;
    static const int32_t EN_US_ADDITIONAL_E[];
    static const int EN_US_ADDITIONAL_I_SIZE = 4;
    static const int32_t EN_US_ADDITIONAL_I[];
    static const int EN_US_ADDITIONAL_O_SIZE = 4;
    static const int32_t EN_US_ADDITIONAL_O[];
    static const int EN_US_ADDITIONAL_U_SIZE = 4;
    static const int32_t EN_US_ADDITIONAL_U[];

    static bool isEnLocale(const std::string *locale_str) {
        return locale_str && locale_str->size() >= LOCALE_EN_US.size()
                && LOCALE_EN_US.compare(0, LOCALE_EN_US.size(), *locale_str);
    }

 public:
    static int getAdditionalCharsSize(const std::string* locale_str, const int32_t c) {
        if (!isEnLocale(locale_str)) {
            return 0;
        }
        switch(c) {
        case 'a':
            return EN_US_ADDITIONAL_A_SIZE;
        case 'e':
            return EN_US_ADDITIONAL_E_SIZE;
        case 'i':
            return EN_US_ADDITIONAL_I_SIZE;
        case 'o':
            return EN_US_ADDITIONAL_O_SIZE;
        case 'u':
            return EN_US_ADDITIONAL_U_SIZE;
        default:
            return 0;
        }
    }

    static const int32_t* getAdditionalChars(const std::string *locale_str, const int32_t c) {
        if (!isEnLocale(locale_str)) {
            return 0;
        }
        switch(c) {
        case 'a':
            return EN_US_ADDITIONAL_A;
        case 'e':
            return EN_US_ADDITIONAL_E;
        case 'i':
            return EN_US_ADDITIONAL_I;
        case 'o':
            return EN_US_ADDITIONAL_O;
        case 'u':
            return EN_US_ADDITIONAL_U;
        default:
            return 0;
        }
    }

    static bool hasAdditionalChars(const std::string *locale_str, const int32_t c) {
        return getAdditionalCharsSize(locale_str, c) > 0;
    }
};

}

#endif // LATINIME_ADDITIONAL_PROXIMITY_CHARS_H
