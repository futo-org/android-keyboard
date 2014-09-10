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

package com.android.inputmethod.latin.personalization;

import java.util.Collections;
import java.util.List;

public class PersonalizationDataChunk {
    public static final String LANGUAGE_UNKNOWN = "";

    public final boolean mInputByUser;
    public final List<String> mTokens;
    public final int mTimestampInSeconds;
    public final String mPackageName;
    public final String mDetectedLanguage;

    public PersonalizationDataChunk(boolean inputByUser, final List<String> tokens,
            final int timestampInSeconds, final String packageName, final String detectedLanguage) {
        mInputByUser = inputByUser;
        mTokens = Collections.unmodifiableList(tokens);
        mTimestampInSeconds = timestampInSeconds;
        mPackageName = packageName;
        mDetectedLanguage = detectedLanguage;
    }
}
