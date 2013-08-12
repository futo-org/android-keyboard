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

#include \
    "suggest/policyimpl/dictionary/shortcut/binary_dictionary_terminal_attributes_reading_utils.h"

#include "suggest/core/dictionary/byte_array_utils.h"

namespace latinime {

typedef BinaryDictionaryTerminalAttributesReadingUtils TaUtils;

const TaUtils::TerminalAttributeFlags TaUtils::MASK_ATTRIBUTE_ADDRESS_TYPE = 0x30;
const TaUtils::TerminalAttributeFlags TaUtils::FLAG_ATTRIBUTE_ADDRESS_TYPE_ONEBYTE = 0x10;
const TaUtils::TerminalAttributeFlags TaUtils::FLAG_ATTRIBUTE_ADDRESS_TYPE_TWOBYTES = 0x20;
const TaUtils::TerminalAttributeFlags TaUtils::FLAG_ATTRIBUTE_ADDRESS_TYPE_THREEBYTES = 0x30;
const TaUtils::TerminalAttributeFlags TaUtils::FLAG_ATTRIBUTE_OFFSET_NEGATIVE = 0x40;
// Flag for presence of more attributes
const TaUtils::TerminalAttributeFlags TaUtils::FLAG_ATTRIBUTE_HAS_NEXT = 0x80;
// Mask for attribute probability, stored on 4 bits inside the flags byte.
const TaUtils::TerminalAttributeFlags TaUtils::MASK_ATTRIBUTE_PROBABILITY = 0x0F;
const int TaUtils::ATTRIBUTE_ADDRESS_SHIFT = 4;
const int TaUtils::SHORTCUT_LIST_SIZE_FIELD_SIZE = 2;
// The numeric value of the shortcut probability that means 'whitelist'.
const int TaUtils::WHITELIST_SHORTCUT_PROBABILITY = 15;

} // namespace latinime
