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

    public static final String DECODER_DICT_SUFFIX = "";

    public static final boolean SHOULD_VERIFY_MAGIC_NUMBER = true;
    public static final boolean SHOULD_VERIFY_CHECKSUM = true;
    public static final boolean SHOULD_USE_DICT_VERSION = true;
    public static final boolean SHOULD_AUTO_CORRECT_USING_NON_WHITE_LISTED_SUGGESTION = false;
    public static final boolean SHOULD_REMOVE_PREVIOUSLY_REJECTED_SUGGESTION = true;
}
