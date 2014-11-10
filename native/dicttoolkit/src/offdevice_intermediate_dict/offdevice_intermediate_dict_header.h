/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef LATINIME_DICT_TOOLKIT_OFFDEVICE_INTERMEDIATE_DICT_HEADER_H
#define LATINIME_DICT_TOOLKIT_OFFDEVICE_INTERMEDIATE_DICT_HEADER_H

#include <map>
#include <vector>

#include "dict_toolkit_defines.h"

namespace latinime {
namespace dicttoolkit {

class OffdeviceIntermediateDictHeader final {
 public:
    using AttributeMap = std::map<std::vector<int>, std::vector<int>>;

    OffdeviceIntermediateDictHeader(const AttributeMap &attributesMap)
            : mAttributeMap(attributesMap) {}

 private:
    DISALLOW_DEFAULT_CONSTRUCTOR(OffdeviceIntermediateDictHeader);
    DISALLOW_ASSIGNMENT_OPERATOR(OffdeviceIntermediateDictHeader);

    const AttributeMap mAttributeMap;
};

} // namespace dicttoolkit
} // namespace latinime
#endif // LATINIME_DICT_TOOLKIT_OFFDEVICE_INTERMEDIATE_DICT_HEADER_H
