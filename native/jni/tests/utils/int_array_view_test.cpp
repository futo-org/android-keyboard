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

TEST(IntArrayViewTest, TestAccess) {
    const std::vector<int> intVector = {3, 2, 1, 0, -1, -2};
    IntArrayView intArrayView(intVector);
    EXPECT_EQ(intVector.size(), intArrayView.size());
    for (int i = 0; i < static_cast<int>(intVector.size()); ++i) {
        EXPECT_EQ(intVector[i], intArrayView[i]);
    }
}

TEST(IntArrayViewTest, TestIteration) {
    const std::vector<int> intVector = {3, 2, 1, 0, -1, -2};
    IntArrayView intArrayView(intVector);
    size_t expectedIndex = 0;
    for (const int element : intArrayView) {
        EXPECT_EQ(intVector[expectedIndex], element);
        ++expectedIndex;
    }
    EXPECT_EQ(expectedIndex, intArrayView.size());
}

TEST(IntArrayViewTest, TestConstructFromArray) {
    const size_t ARRAY_SIZE = 100;
    int intArray[ARRAY_SIZE];
    const auto intArrayView = IntArrayView::fromFixedSizeArray(intArray);
    EXPECT_EQ(ARRAY_SIZE, intArrayView.size());
}

TEST(IntArrayViewTest, TestConstructFromObject) {
    const int object = 10;
    const auto intArrayView = IntArrayView::fromObject(&object);
    EXPECT_EQ(1, intArrayView.size());
    EXPECT_EQ(object, intArrayView[0]);
}

}  // namespace
}  // namespace latinime
