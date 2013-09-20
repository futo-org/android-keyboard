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

#include "suggest/policyimpl/dictionary/dynamic_patricia_trie_gc_event_listeners.h"

namespace latinime {

bool DynamicPatriciaTrieGcEventListeners
        ::ListenerForUpdatingUnigramProbabilityAndMarkingUselessPtNodesAsDeleted
                ::onVisitingPtNode(const DynamicPatriciaTrieNodeReader *const node) {
    // PtNode is useless when the PtNode is not a terminal and doesn't have any not useless
    // children.
    bool isUselessPtNode = !node->isTerminal();
    if (mChildrenValue > 0) {
        isUselessPtNode = false;
    } else if (node->isTerminal()) {
        // Remove children as all children are useless.
        int writingPos = node->getChildrenPosFieldPos();
        if (!DynamicPatriciaTrieWritingUtils::writeChildrenPositionAndAdvancePosition(
                mBuffer, NOT_A_DICT_POS /* childrenPosition */, &writingPos)) {
            return false;
        }
    }
    if (isUselessPtNode) {
        // Current PtNode is no longer needed. Mark it as deleted.
        if (!mWritingHelper->markNodeAsDeleted(node)) {
            return false;
        }
    } else {
        valueStack.back() += 1;
    }
    return true;
}

} // namespace latinime
