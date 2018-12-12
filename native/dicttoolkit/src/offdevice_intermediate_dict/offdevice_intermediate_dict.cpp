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

#include "offdevice_intermediate_dict/offdevice_intermediate_dict.h"

#include "offdevice_intermediate_dict/offdevice_intermediate_dict_pt_node.h"

namespace latinime {
namespace dicttoolkit {

bool OffdeviceIntermediateDict::addWord(const WordProperty &wordProperty) {
    const CodePointArrayView codePoints = wordProperty.getCodePoints();
    if (codePoints.empty() || codePoints.size() > MAX_WORD_LENGTH) {
        return false;
    }
    return addWordInner(codePoints, wordProperty, mRootPtNodeArray);
}

bool OffdeviceIntermediateDict::addWordInner(const CodePointArrayView codePoints,
        const WordProperty &wordProperty, OffdeviceIntermediateDictPtNodeArray &ptNodeArray) {
    auto ptNodeList = ptNodeArray.getMutablePtNodeList();
    auto ptNodeIt = ptNodeList->begin();
    for (; ptNodeIt != ptNodeList->end(); ++ptNodeIt) {
        const auto &ptNode = *ptNodeIt;
        const CodePointArrayView ptNodeCodePoints = ptNode->getPtNodeCodePoints();
        if (codePoints[0] < ptNodeCodePoints[0]) {
            continue;
        }
        if (codePoints[0] > ptNodeCodePoints[0]) {
            break;
        }
        size_t i = 1;
        for (; i < codePoints.size(); ++i) {
            if (i >= ptNodeCodePoints.size()) {
                // Add new child.
                return addWordInner(codePoints.skip(i), wordProperty,
                        ptNode->getChildrenPtNodeArray());
            }
            if (codePoints[i] != ptNodeCodePoints[i]) {
                break;
            }
        }
        if (codePoints.size() == i && codePoints.size() == ptNodeCodePoints.size()) {
            // All code points matched.
            if (ptNode->getWordProperty()) {
                //  Adding the same word multiple times is not supported.
                return false;
            }
            ptNodeList->insert(ptNodeIt,
                    std::make_shared<OffdeviceIntermediateDictPtNode>(wordProperty, *ptNode));
            ptNodeList->erase(ptNodeIt);
            return true;
        }
        // The (i+1)-th elements are different.
        // Create and Add new parent ptNode for the common part.
        auto newPtNode = codePoints.size() == i
                ? std::make_shared<OffdeviceIntermediateDictPtNode>(codePoints, wordProperty)
                : std::make_shared<OffdeviceIntermediateDictPtNode>(codePoints.limit(i));
        ptNodeList->insert(ptNodeIt, newPtNode);
        OffdeviceIntermediateDictPtNodeArray &childrenPtNodeArray =
                newPtNode->getChildrenPtNodeArray();
        // Add new child for the existing ptNode.
        childrenPtNodeArray.getMutablePtNodeList()->push_back(
                std::make_shared<OffdeviceIntermediateDictPtNode>(
                        ptNodeCodePoints.skip(i), *ptNode));
        ptNodeList->erase(ptNodeIt);
        if (codePoints.size() != i) {
            // Add a child for the new word.
            return addWordInner(codePoints.skip(i), wordProperty, childrenPtNodeArray);
        }
        return true;
    }
    ptNodeList->insert(ptNodeIt,
            std::make_shared<OffdeviceIntermediateDictPtNode>(codePoints, wordProperty));
    return true;
}

const WordProperty *OffdeviceIntermediateDict::getWordProperty(
        const CodePointArrayView codePoints) const {
    const OffdeviceIntermediateDictPtNodeArray *ptNodeArray = &mRootPtNodeArray;
    for (size_t i = 0; i < codePoints.size();) {
        bool foundNext = false;
        for (const auto& ptNode : ptNodeArray->getPtNodeList()) {
            const CodePointArrayView ptNodeCodePoints = ptNode->getPtNodeCodePoints();
            if (codePoints[i] < ptNodeCodePoints[0]) {
                continue;
            }
            if (codePoints[i] > ptNodeCodePoints[0]
                     || codePoints.size() < ptNodeCodePoints.size()) {
                return nullptr;
            }
            for (size_t j = 1; j < ptNodeCodePoints.size(); ++j) {
                if (codePoints[i + j] != ptNodeCodePoints[j]) {
                    return nullptr;
                }
            }
            i += ptNodeCodePoints.size();
            if (i == codePoints.size()) {
                return ptNode->getWordProperty();
            }
            ptNodeArray = &ptNode->getChildrenPtNodeArray();
            foundNext = true;
            break;
        }
        if (!foundNext) {
            break;
        }
    }
    return nullptr;
}

} // namespace dicttoolkit
} // namespace latinime
