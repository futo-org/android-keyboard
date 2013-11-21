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

#ifndef LATINIME_PT_NODE_WRITER_H
#define LATINIME_PT_NODE_WRITER_H

#include "defines.h"

#include "suggest/policyimpl/dictionary/structure/pt_common/pt_node_params.h"
#include "utils/hash_map_compat.h"

namespace latinime {

// Interface class used to write PtNode information.
class PtNodeWriter {
 public:
    typedef hash_map_compat<int, int> PtNodeArrayPositionRelocationMap;
    typedef hash_map_compat<int, int> PtNodePositionRelocationMap;
    struct DictPositionRelocationMap {
     public:
        DictPositionRelocationMap()
                : mPtNodeArrayPositionRelocationMap(), mPtNodePositionRelocationMap() {}

        PtNodeArrayPositionRelocationMap mPtNodeArrayPositionRelocationMap;
        PtNodePositionRelocationMap mPtNodePositionRelocationMap;

     private:
        DISALLOW_COPY_AND_ASSIGN(DictPositionRelocationMap);
    };

    virtual ~PtNodeWriter() {}

    virtual bool markPtNodeAsDeleted(const PtNodeParams *const toBeUpdatedPtNodeParams) = 0;

    virtual bool markPtNodeAsMoved(const PtNodeParams *const toBeUpdatedPtNodeParams,
            const int movedPos, const int bigramLinkedNodePos) = 0;

    virtual bool updatePtNodeProbability(const PtNodeParams *const toBeUpdatedPtNodeParams,
            const int probability) = 0;

    virtual bool updateChildrenPosition(const PtNodeParams *const toBeUpdatedPtNodeParams,
                const int newChildrenPosition) = 0;

    virtual bool writePtNodeAndAdvancePosition(const PtNodeParams *const ptNodeParams,
            int *const ptNodeWritingPos) = 0;

    virtual bool addNewBigramEntry(const PtNodeParams *const sourcePtNodeParams,
            const PtNodeParams *const targetPtNodeParam, const int probability,
            bool *const outAddedNewBigram) = 0;

    virtual bool removeBigramEntry(const PtNodeParams *const sourcePtNodeParams,
            const PtNodeParams *const targetPtNodeParam) = 0;

    virtual bool updateAllBigramEntriesAndDeleteUselessEntries(
            const PtNodeParams *const sourcePtNodeParams, int *const outBigramEntryCount) = 0;

    virtual bool updateAllPositionFields(const PtNodeParams *const toBeUpdatedPtNodeParams,
            const DictPositionRelocationMap *const dictPositionRelocationMap,
            int *const outBigramEntryCount) = 0;

 protected:
    PtNodeWriter() {};

 private:
    DISALLOW_COPY_AND_ASSIGN(PtNodeWriter);
};
} // namespace latinime
#endif /* LATINIME_PT_NODE_WRITER_H */
