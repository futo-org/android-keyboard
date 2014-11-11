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

#ifndef LATINIME_DICT_TOOLKIT_OFFDEVICE_INTERMEDIATE_DICT_PT_NODE_H
#define LATINIME_DICT_TOOLKIT_OFFDEVICE_INTERMEDIATE_DICT_PT_NODE_H

#include <memory>

#include "dict_toolkit_defines.h"
#include "offdevice_intermediate_dict/offdevice_intermediate_dict_pt_node_array.h"
#include "suggest/core/dictionary/property/word_property.h"
#include "utils/int_array_view.h"

namespace latinime {
namespace dicttoolkit {

class OffdeviceIntermediateDictPtNode final {
 public:
    // Non-terminal
    OffdeviceIntermediateDictPtNode(const CodePointArrayView ptNodeCodePoints)
            : mPtNodeCodePoints(ptNodeCodePoints.toVector()), mChildrenPtNodeArray(),
              mWortProperty(nullptr) {}

    // Terminal
    OffdeviceIntermediateDictPtNode(const CodePointArrayView ptNodeCodePoints,
            const WordProperty &wordProperty)
             : mPtNodeCodePoints(ptNodeCodePoints.toVector()), mChildrenPtNodeArray(),
               mWortProperty(new WordProperty(wordProperty)) {}

    // Replacing PtNodeCodePoints.
    OffdeviceIntermediateDictPtNode(const CodePointArrayView ptNodeCodePoints,
            const OffdeviceIntermediateDictPtNode &ptNode)
            : mPtNodeCodePoints(ptNodeCodePoints.toVector()),
              mChildrenPtNodeArray(ptNode.mChildrenPtNodeArray),
              mWortProperty(new WordProperty(*ptNode.mWortProperty)) {}

    // Replacing WordProperty.
    OffdeviceIntermediateDictPtNode(const WordProperty &wordProperty,
            const OffdeviceIntermediateDictPtNode &ptNode)
            : mPtNodeCodePoints(ptNode.mPtNodeCodePoints),
              mChildrenPtNodeArray(ptNode.mChildrenPtNodeArray),
              mWortProperty(new WordProperty(wordProperty)) {}

    const WordProperty *getWordProperty() const {
        return mWortProperty.get();
    }

    const CodePointArrayView getPtNodeCodePoints() const {
        return CodePointArrayView(mPtNodeCodePoints);
    }

    OffdeviceIntermediateDictPtNodeArray &getChildrenPtNodeArray() {
        return mChildrenPtNodeArray;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(OffdeviceIntermediateDictPtNode);

    const std::vector<int> mPtNodeCodePoints;
    OffdeviceIntermediateDictPtNodeArray mChildrenPtNodeArray;
    const std::unique_ptr<WordProperty> mWortProperty;
};

} // namespace dicttoolkit
} // namespace latinime
#endif // LATINIME_DICT_TOOLKIT_OFFDEVICE_INTERMEDIATE_DICT_PT_NODE_H
