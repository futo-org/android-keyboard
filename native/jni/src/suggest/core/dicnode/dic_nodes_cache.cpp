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

#include <list>

#include "defines.h"
#include "suggest/core/dicnode/dic_node_priority_queue.h"
#include "suggest/core/dicnode/dic_node_utils.h"
#include "suggest/core/dicnode/dic_nodes_cache.h"

namespace latinime {

// The biggest value among MAX_CACHE_DIC_NODE_SIZE, MAX_CACHE_DIC_NODE_SIZE_FOR_SINGLE_POINT, ...
const int DicNodesCache::LARGE_PRIORITY_QUEUE_CAPACITY = 310;
// Capacity for reducing memory footprint.
const int DicNodesCache::SMALL_PRIORITY_QUEUE_CAPACITY = 100;

/**
 * Truncates all of the dicNodes so that they start at the given commit point.
 * Only called for multi-word typing input.
 */
DicNode *DicNodesCache::setCommitPoint(int commitPoint) {
    std::list<DicNode> dicNodesList;
    while (mCachedDicNodesForContinuousSuggestion->getSize() > 0) {
        DicNode dicNode;
        mCachedDicNodesForContinuousSuggestion->copyPop(&dicNode);
        dicNodesList.push_front(dicNode);
    }

    // Get the starting words of the top scoring dicNode (last dicNode popped from priority queue)
    // up to the commit point. These words have already been committed to the text view.
    DicNode *topDicNode = &dicNodesList.front();
    DicNode topDicNodeCopy;
    DicNodeUtils::initByCopy(topDicNode, &topDicNodeCopy);

    // Keep only those dicNodes that match the same starting words.
    std::list<DicNode>::iterator iter;
    for (iter = dicNodesList.begin(); iter != dicNodesList.end(); iter++) {
        DicNode *dicNode = &*iter;
        if (dicNode->truncateNode(&topDicNodeCopy, commitPoint)) {
            mCachedDicNodesForContinuousSuggestion->copyPush(dicNode);
        } else {
            // Top dicNode should be reprocessed.
            ASSERT(dicNode != topDicNode);
            DicNode::managedDelete(dicNode);
        }
    }
    mInputIndex -= commitPoint;
    return topDicNode;
}
}  // namespace latinime
