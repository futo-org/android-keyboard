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

package com.android.inputmethod.latin.common;

import javax.annotation.Nonnull;

public final class CoordinateUtils {
    private static final int INDEX_X = 0;
    private static final int INDEX_Y = 1;
    private static final int ELEMENT_SIZE = INDEX_Y + 1;

    private CoordinateUtils() {
        // This utility class is not publicly instantiable.
    }

    @Nonnull
    public static int[] newInstance() {
        return new int[ELEMENT_SIZE];
    }

    public static int x(@Nonnull final int[] coords) {
        return coords[INDEX_X];
    }

    public static int y(@Nonnull final int[] coords) {
        return coords[INDEX_Y];
    }

    public static void set(@Nonnull final int[] coords, final int x, final int y) {
        coords[INDEX_X] = x;
        coords[INDEX_Y] = y;
    }

    public static void copy(@Nonnull final int[] destination, @Nonnull final int[] source) {
        destination[INDEX_X] = source[INDEX_X];
        destination[INDEX_Y] = source[INDEX_Y];
    }

    @Nonnull
    public static int[] newCoordinateArray(final int arraySize) {
        return new int[ELEMENT_SIZE * arraySize];
    }

    @Nonnull
    public static int[] newCoordinateArray(final int arraySize,
            final int defaultX, final int defaultY) {
        final int[] result = new int[ELEMENT_SIZE * arraySize];
        for (int i = 0; i < arraySize; ++i) {
            setXYInArray(result, i, defaultX, defaultY);
        }
        return result;
    }

    public static int xFromArray(@Nonnull final int[] coordsArray, final int index) {
        return coordsArray[ELEMENT_SIZE * index + INDEX_X];
    }

    public static int yFromArray(@Nonnull final int[] coordsArray, final int index) {
        return coordsArray[ELEMENT_SIZE * index + INDEX_Y];
    }

    @Nonnull
    public static int[] coordinateFromArray(@Nonnull final int[] coordsArray, final int index) {
        final int[] coords = newInstance();
        set(coords, xFromArray(coordsArray, index), yFromArray(coordsArray, index));
        return coords;
    }

    public static void setXYInArray(@Nonnull final int[] coordsArray, final int index,
            final int x, final int y) {
        final int baseIndex = ELEMENT_SIZE * index;
        coordsArray[baseIndex + INDEX_X] = x;
        coordsArray[baseIndex + INDEX_Y] = y;
    }

    public static void setCoordinateInArray(@Nonnull final int[] coordsArray, final int index,
            @Nonnull final int[] coords) {
        setXYInArray(coordsArray, index, x(coords), y(coords));
    }
}
