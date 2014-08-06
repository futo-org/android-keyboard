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

#include "utils/int_array_view.h"

#include <gtest/gtest.h>

#include <vector>

namespace latinime {
namespace {

TEST(MemoryViewTest, TestAccess) {
    std::vector<int> intVector = {3, 2, 1, 0, -1, -2};
    IntArrayView intArrayView(intVector);
    EXPECT_EQ(intVector.size(), intArrayView.size());
    for (int i = 0; i < static_cast<int>(intVector.size()); ++i) {
        EXPECT_EQ(intVector[i], intArrayView[i]);
    }
}

TEST(MemoryViewTest, TestIteration) {
    std::vector<int> intVector = {3, 2, 1, 0, -1, -2};
    IntArrayView intArrayView(intVector);
    std::set<int> intSet(intVector.begin(), intVector.end());
    for (const int i : intArrayView) {
        EXPECT_TRUE(intSet.count(i) > 0);
        intSet.erase(i);
    }
    EXPECT_TRUE(intSet.empty());
}

}  // namespace
}  // namespace latinime
