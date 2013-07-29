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

#ifndef LATINIME_DIC_NODE_RELEASE_LISTENER_H
#define LATINIME_DIC_NODE_RELEASE_LISTENER_H

#include "defines.h"

namespace latinime {

class DicNode;

class DicNodeReleaseListener {
 public:
    DicNodeReleaseListener() {}
    virtual ~DicNodeReleaseListener() {}
    virtual void onReleased(DicNode *dicNode) = 0;
 private:
    DISALLOW_COPY_AND_ASSIGN(DicNodeReleaseListener);
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_RELEASE_LISTENER_H
