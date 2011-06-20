/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.compat;

import java.lang.reflect.Method;
import java.util.Arrays;

public class ArraysCompatUtils {
    private static final Method METHOD_Arrays_binarySearch = CompatUtils
            .getMethod(Arrays.class, "binarySearch", int[].class, int.class, int.class, int.class);

    public static int binarySearch(int[] array, int startIndex, int endIndex, int value) {
        if (METHOD_Arrays_binarySearch != null) {
            final Object index = CompatUtils.invoke(null, 0, METHOD_Arrays_binarySearch,
                    array, startIndex, endIndex, value);
            return (Integer)index;
        } else {
            return compatBinarySearch(array, startIndex, endIndex, value);
        }
    }

    /* package */ static int compatBinarySearch(int[] array, int startIndex, int endIndex,
            int value) {
        if (startIndex > endIndex) throw new IllegalArgumentException();
        if (startIndex < 0 || endIndex > array.length) throw new ArrayIndexOutOfBoundsException();

        final int work[] = new int[endIndex - startIndex];
        System.arraycopy(array, startIndex, work, 0, work.length);
        final int index = Arrays.binarySearch(work, value);
        if (index >= 0) {
            return index + startIndex;
        } else {
            return ~(~index + startIndex);
        }
    }
}
