/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.inputmethod.latin.define;

/**
 * Decoder specific constants for LatinIme.
 */
public class DecoderSpecificConstants {

    // Must be equal to MAX_WORD_LENGTH in native/jni/src/defines.h
    public static final int DICTIONARY_MAX_WORD_LENGTH = 48;

    // (MAX_PREV_WORD_COUNT_FOR_N_GRAM + 1)-gram is supported in Java side. Needs to modify
    // MAX_PREV_WORD_COUNT_FOR_N_GRAM in native/jni/src/defines.h for suggestions.
    public static final int MAX_PREV_WORD_COUNT_FOR_N_GRAM = 3;
}
