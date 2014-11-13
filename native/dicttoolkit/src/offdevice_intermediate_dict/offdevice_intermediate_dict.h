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

#ifndef LATINIME_DICT_TOOLKIT_OFFDEVICE_INTERMEDIATE_DICT_H
#define LATINIME_DICT_TOOLKIT_OFFDEVICE_INTERMEDIATE_DICT_H

#include "dict_toolkit_defines.h"
#include "offdevice_intermediate_dict/offdevice_intermediate_dict_header.h"
#include "offdevice_intermediate_dict/offdevice_intermediate_dict_pt_node_array.h"
#include "suggest/core/dictionary/property/word_property.h"
#include "utils/int_array_view.h"

namespace latinime {
namespace dicttoolkit {

/**
 * On memory patricia trie to represent a dictionary.
 */
class OffdeviceIntermediateDict final {
 public:
    OffdeviceIntermediateDict(const OffdeviceIntermediateDictHeader &header)
            : mHeader(header), mRootPtNodeArray() {}

    bool addWord(const WordProperty &wordProperty);
    // The returned value will be invalid after modifying the dictionary. e.g. calling addWord().
    const WordProperty *getWordProperty(const CodePointArrayView codePoints) const;
    const OffdeviceIntermediateDictHeader &getHeader() const { return mHeader; }

 private:
    DISALLOW_ASSIGNMENT_OPERATOR(OffdeviceIntermediateDict);

    const OffdeviceIntermediateDictHeader mHeader;
    OffdeviceIntermediateDictPtNodeArray mRootPtNodeArray;

    bool addWordInner(const CodePointArrayView codePoints, const WordProperty &wordProperty,
            OffdeviceIntermediateDictPtNodeArray &ptNodeArray);
};

} // namespace dicttoolkit
} // namespace latinime
#endif // LATINIME_DICT_TOOLKIT_OFFDEVICE_INTERMEDIATE_DICT_H
