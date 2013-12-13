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

#ifndef LATINIME_DICTIONARY_STRUCTURE_WITH_BUFFER_POLICY_FACTORY_H
#define LATINIME_DICTIONARY_STRUCTURE_WITH_BUFFER_POLICY_FACTORY_H

#include <stdint.h>

#include "defines.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "utils/exclusive_ownership_pointer.h"

namespace latinime {

class DictionaryStructureWithBufferPolicyFactory {
 public:
    static DictionaryStructureWithBufferPolicy::StructurePolicyPtr
            newDictionaryStructureWithBufferPolicy(const char *const path, const int bufOffset,
                    const int size, const bool isUpdatable);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DictionaryStructureWithBufferPolicyFactory);

    static DictionaryStructureWithBufferPolicy::StructurePolicyPtr
            newPolicyforDirectoryDict(const char *const path, const bool isUpdatable);

    static DictionaryStructureWithBufferPolicy::StructurePolicyPtr
            newPolicyforFileDict(const char *const path, const int bufOffset, const int size);

    static void getHeaderFilePathInDictDir(const char *const dirPath,
            const int outHeaderFileBufSize, char *const outHeaderFilePath);
};
} // namespace latinime
#endif // LATINIME_DICTIONARY_STRUCTURE_WITH_BUFFER_POLICY_FACTORY_H
